package io.github.nishantshah0.papertrader.trading;

import io.github.nishantshah0.papertrader.NotFoundException;
import io.github.nishantshah0.papertrader.account.Account;
import io.github.nishantshah0.papertrader.account.AccountRepository;
import io.github.nishantshah0.papertrader.quote.Quote;
import io.github.nishantshah0.papertrader.quote.QuoteCache;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final AccountRepository accounts;
    private final OrderRepository orders;
    private final TradeRepository trades;
    private final PositionRepository positions;
    private final QuoteCache quotes;
    private final org.springframework.context.ApplicationEventPublisher events;

    OrderService(AccountRepository accounts, OrderRepository orders, TradeRepository trades,
            PositionRepository positions, QuoteCache quotes, org.springframework.context.ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.orders = orders;
        this.trades = trades;
        this.positions = positions;
        this.quotes = quotes;
        this.events = events;
    }

    // one transaction per order: the account row lock serialises orders on the same
    // account, and the cash, position, order and trade changes commit or roll back together
    @Transactional
    public Order place(Long accountId, PlaceOrderRequest request) {
        return place(accountId, request, null);
    }

    @Transactional
    public Order place(Long accountId, PlaceOrderRequest request, String key) {
        Account account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new NotFoundException("account " + accountId + " not found"));
        if (key != null) {
            if (key.isBlank() || key.length() > 128) throw new OrderRejectedException("Idempotency-Key must contain 1 to 128 characters");
            var previous = orders.findByAccountIdAndIdempotencyKey(accountId, key);
            if (previous.isPresent()) {
                if (!previous.get().sameRequest(request)) throw new OrderStateException("Idempotency-Key was already used for a different order");
                return previous.get();
            }
        }
        String symbol = request.symbol().toUpperCase(Locale.ROOT);
        Quote quote = quotes.get(symbol)
                .orElseThrow(() -> new OrderRejectedException("unknown symbol " + symbol));
        if ((request.typeOrMarket() == OrderType.LIMIT) != (request.limitPrice() != null)) {
            throw new OrderRejectedException("limit orders require limitPrice; market orders must omit it");
        }
        Order order = request.typeOrMarket() == OrderType.LIMIT
                ? Order.limit(accountId, symbol, request.side(), request.quantity(), request.limitPrice())
                : Order.market(accountId, symbol, request.side(), request.quantity());
        checkResources(account, order, order.getType() == OrderType.LIMIT ? order.getLimitPrice() : quote.price());
        order.setIdempotencyKey(key);
        orders.save(order);
        quotes.requireTradable(quote);
        if (order.crosses(quote.price())) fill(account, order, quote.price());
        events.publishEvent(new OrderChanged(accountId, order.getId(), order.getStatus()));
        return order;
    }

    // The scheduler passes IDs, never managed entities: acquire the account lock before
    // reading current order state so cancellation and repeated ticks cannot double-fill.
    @Transactional
    public void match(Long accountId, Long orderId) {
        Account account = accounts.findByIdForUpdate(accountId).orElseThrow();
        Order order = find(accountId, orderId);
        if (!order.isOpen()) return;
        Quote quote = quotes.get(order.getSymbol()).orElse(null);
        if (quote == null || !quotes.isTradable(quote) || !order.crosses(quote.price())) return;
        try {
            checkResources(account, order, quote.price());
        } catch (OrderRejectedException e) {
            order.reject();
            events.publishEvent(new OrderChanged(accountId, orderId, order.getStatus()));
            return;
        }
        fill(account, order, quote.price());
        events.publishEvent(new OrderChanged(accountId, orderId, order.getStatus()));
    }

    // Open orders do not reserve cash/shares. Revalidate under the same account lock
    // at execution; an unfunded resting order becomes REJECTED without a trade.
    private void checkResources(Account account, Order order, BigDecimal price) {
        BigDecimal cost = price.multiply(BigDecimal.valueOf(order.getQuantity())).setScale(2, RoundingMode.HALF_UP);
        if (order.getSide() == OrderSide.BUY && !account.canAfford(cost)) {
            throw new OrderRejectedException("insufficient cash");
        }
        int held = positions.findByAccountIdAndSymbol(order.getAccountId(), order.getSymbol())
                .map(Position::getQuantity).orElse(0);
        if (order.getSide() == OrderSide.SELL && held < order.getQuantity()) {
            throw new OrderRejectedException("insufficient shares");
        }
        if (order.getSide() == OrderSide.BUY && (long) held + order.getQuantity() > Integer.MAX_VALUE) {
            throw new OrderRejectedException("position quantity exceeds supported maximum");
        }
    }

    private void fill(Account account, Order order, BigDecimal price) {
        BigDecimal notional = price.multiply(BigDecimal.valueOf(order.getQuantity())).setScale(2, RoundingMode.HALF_UP);
        Position position = positions.findByAccountIdAndSymbol(order.getAccountId(), order.getSymbol())
                .orElseGet(() -> new Position(order.getAccountId(), order.getSymbol()));
        switch (order.getSide()) {
            case BUY -> {
                if (!account.canAfford(notional)) {
                    throw new OrderRejectedException("insufficient cash: order costs " + notional
                            + ", account has " + account.getCash());
                }
                account.debit(notional);
                position.buy(order.getQuantity(), price);
            }
            case SELL -> {
                if (position.getQuantity() < order.getQuantity()) {
                    throw new OrderRejectedException("insufficient shares: order sells " + order.getQuantity()
                            + " " + order.getSymbol() + ", account holds " + position.getQuantity());
                }
                position.sell(order.getQuantity(), price);
                account.credit(notional);
            }
        }
        order.fill();
        positions.save(position);
        trades.save(new Trade(order, price));
    }

    @Transactional
    public Order cancel(Long accountId, Long orderId) {
        accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new NotFoundException("account " + accountId + " not found"));
        Order order = find(accountId, orderId);
        if (!order.isOpen()) {
            throw new OrderStateException("order " + orderId + " is " + order.getStatus()
                    + ", only OPEN orders can be cancelled");
        }
        order.cancel();
        events.publishEvent(new OrderChanged(accountId, orderId, order.getStatus()));
        return order;
    }

    @Transactional(readOnly = true)
    public Order get(Long accountId, Long orderId) {
        return find(accountId, orderId);
    }

    @Transactional(readOnly = true)
    public List<Order> list(Long accountId, OrderStatus status) {
        requireAccount(accountId);
        return status == null
                ? orders.findByAccountIdOrderByCreatedAtDesc(accountId)
                : orders.findByAccountIdAndStatusOrderByCreatedAtDesc(accountId, status);
    }

    @Transactional(readOnly = true)
    public List<Trade> trades(Long accountId) {
        requireAccount(accountId);
        return trades.findByAccountIdOrderByExecutedAtDesc(accountId);
    }

    private Order find(Long accountId, Long orderId) {
        return orders.findByIdAndAccountId(orderId, accountId)
                .orElseThrow(() -> new NotFoundException("order " + orderId + " not found"));
    }

    private void requireAccount(Long accountId) {
        if (!accounts.existsById(accountId)) {
            throw new NotFoundException("account " + accountId + " not found");
        }
    }
}
