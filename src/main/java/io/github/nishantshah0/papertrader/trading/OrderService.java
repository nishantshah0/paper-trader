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

    OrderService(AccountRepository accounts, OrderRepository orders, TradeRepository trades,
            PositionRepository positions, QuoteCache quotes) {
        this.accounts = accounts;
        this.orders = orders;
        this.trades = trades;
        this.positions = positions;
        this.quotes = quotes;
    }

    // one transaction per order: the account row lock serialises orders on the same
    // account, and the cash, position, order and trade changes commit or roll back together
    @Transactional
    public Order place(Long accountId, PlaceOrderRequest request) {
        Account account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new NotFoundException("account " + accountId + " not found"));
        String symbol = request.symbol().toUpperCase(Locale.ROOT);
        Quote quote = quotes.get(symbol)
                .orElseThrow(() -> new OrderRejectedException("unknown symbol " + symbol));
        if (request.typeOrMarket() == OrderType.LIMIT) {
            throw new OrderRejectedException("limit orders are not supported yet");
        }
        Order order = orders.save(Order.market(accountId, symbol, request.side(), request.quantity()));
        fill(account, order, quote.price());
        return order;
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
