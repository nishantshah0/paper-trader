package io.github.nishantshah0.papertrader.trading;

import io.github.nishantshah0.papertrader.NotFoundException;
import io.github.nishantshah0.papertrader.account.Account;
import io.github.nishantshah0.papertrader.account.AccountRepository;
import io.github.nishantshah0.papertrader.trading.OrderBook.PlannedFill;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The database side of matching. Every method is one transaction, called by the
// MatchingEngine on the matcher thread that owns the symbol's book. A method may read
// the book but never changes it: it returns what changed, and the engine applies that
// to the book only after the transaction has committed.
@Service
class OrderExecution {

    // what a committed transaction did to the book
    sealed interface BookChange {
        record Reduce(long orderId, int shares) implements BookChange {
        }

        record Remove(long orderId) implements BookChange {
        }

        record Rest(RestingOrder order) implements BookChange {
        }
    }

    record Result(Order order, List<BookChange> changes) {
    }

    // thrown when a resting order in the book no longer matches its row; the engine
    // reloads the book from Postgres and retries
    static class BookOutOfSyncException extends IllegalStateException {
        BookOutOfSyncException(String message) {
            super(message);
        }
    }

    private final AccountRepository accounts;
    private final OrderRepository orders;
    private final TradeRepository trades;
    private final PositionRepository positions;
    private final ApplicationEventPublisher events;

    OrderExecution(AccountRepository accounts, OrderRepository orders, TradeRepository trades,
            PositionRepository positions, ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.orders = orders;
        this.trades = trades;
        this.positions = positions;
        this.events = events;
    }

    // Places an order and matches it: first against other users' resting orders the
    // book offers, then against the house for what is left, if the house price is
    // within the limit. A limit order with shares left rests in the book.
    //
    // A trade between two users touches two accounts. Both rows are locked in ID order,
    // the same order every matcher thread uses, so two symbols matching the same pair of
    // accounts at once cannot deadlock.
    @Transactional
    public Result place(Long accountId, PlaceOrderRequest request, String key, String symbol, OrderBook book,
            HouseQuote house) {
        if (key != null) {
            if (key.isBlank() || key.length() > 128) {
                throw new OrderRejectedException("Idempotency-Key must contain 1 to 128 characters");
            }
            var previous = orders.findByAccountIdAndIdempotencyKey(accountId, key);
            if (previous.isPresent()) {
                if (!previous.get().sameRequest(request)) {
                    throw new OrderStateException("Idempotency-Key was already used for a different order");
                }
                return new Result(previous.get(), List.of());
            }
        }
        OrderType type = request.typeOrMarket();
        if ((type == OrderType.LIMIT) != (request.limitPrice() != null)) {
            throw new OrderRejectedException("limit orders require limitPrice; market orders must omit it");
        }
        if (type == OrderType.MARKET && house == null) {
            throw new OrderRejectedException("quote is stale; wait for a fresh market quote");
        }

        List<PlannedFill> planned = book.match(request.side(), request.limitPrice(), request.quantity(),
                accountId, house);
        TreeSet<Long> involved = new TreeSet<>();
        involved.add(accountId);
        planned.forEach(fill -> involved.add(fill.maker().accountId()));
        Map<Long, Account> locked = lockInIdOrder(involved);
        Account account = locked.get(accountId);

        Order order = type == OrderType.LIMIT
                ? Order.limit(accountId, symbol, request.side(), request.quantity(), request.limitPrice())
                : Order.market(accountId, symbol, request.side(), request.quantity());
        // a market buy fills at the house ask or better, so that bounds what it can cost
        BigDecimal worstPrice = type == OrderType.LIMIT ? order.getLimitPrice() : house.priceFor(order.getSide());
        checkAvailable(account, order, worstPrice);
        order.setIdempotencyKey(key);
        orders.save(order);

        List<BookChange> changes = new ArrayList<>();
        for (PlannedFill fill : planned) {
            Order maker = orders.findById(fill.maker().id())
                    .filter(row -> row.isOpen() && row.remainingQuantity() >= fill.quantity())
                    .orElseThrow(() -> new BookOutOfSyncException("order " + fill.maker().id()
                            + " in the " + symbol + " book no longer matches its row"));
            Account makerAccount = locked.get(maker.getAccountId());
            if (!canSettle(makerAccount, maker, fill.quantity(), fill.price())) {
                // only possible for an order placed before reservations existed
                maker.reject();
                changes.add(new BookChange.Remove(maker.getId()));
                publish(maker);
                continue;
            }
            settle(account, order, fill.quantity(), fill.price(), maker.getId());
            settle(makerAccount, maker, fill.quantity(), fill.price(), order.getId());
            changes.add(new BookChange.Reduce(maker.getId(), fill.quantity()));
            publish(maker);
        }
        if (order.isOpen() && house != null && order.crosses(house.priceFor(order.getSide()))) {
            settle(account, order, order.remainingQuantity(), house.priceFor(order.getSide()), null);
        }
        if (order.isOpen()) {
            if (type == OrderType.LIMIT) {
                changes.add(new BookChange.Rest(RestingOrder.of(order)));
            } else {
                order.cancel(); // nothing left to trade with; the filled part stands
            }
        }
        publish(order);
        return new Result(order, changes);
    }

    // A resting order the house quote now crosses fills in full at the house price.
    @Transactional
    public Result fillAgainstHouse(RestingOrder resting, HouseQuote house) {
        long orderId = resting.id();
        Account account = accounts.findByIdForUpdate(resting.accountId()).orElseThrow();
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new BookOutOfSyncException("order " + orderId + " is in the book but not the database"));
        if (!order.isOpen()) {
            return new Result(order, List.of(new BookChange.Remove(orderId)));
        }
        BigDecimal price = house == null ? null : house.priceFor(order.getSide());
        if (price == null || !order.crosses(price)) {
            return new Result(order, List.of());
        }
        if (!canSettle(account, order, order.remainingQuantity(), price)) {
            order.reject();
        } else {
            settle(account, order, order.remainingQuantity(), price, null);
        }
        publish(order);
        return new Result(order, List.of(new BookChange.Remove(orderId)));
    }

    @Transactional
    public Result cancel(Long accountId, Long orderId) {
        accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new NotFoundException("account " + accountId + " not found"));
        Order order = orders.findByIdAndAccountId(orderId, accountId)
                .orElseThrow(() -> new NotFoundException("order " + orderId + " not found"));
        if (!order.isOpen()) {
            throw new OrderStateException("order " + orderId + " is " + order.getStatus()
                    + ", only OPEN orders can be cancelled");
        }
        order.cancel();
        publish(order);
        return new Result(order, List.of(new BookChange.Remove(orderId)));
    }

    private Map<Long, Account> lockInIdOrder(TreeSet<Long> accountIds) {
        Map<Long, Account> locked = new TreeMap<>();
        for (Long id : accountIds) {
            locked.put(id, accounts.findByIdForUpdate(id)
                    .orElseThrow(() -> new NotFoundException("account " + id + " not found")));
        }
        return locked;
    }

    // A new order may only use what open limit orders have not already claimed, so a
    // resting order stays funded until it fills or is cancelled. Buys reserve at their
    // limit and fill at or below it, which means the hold always covers the fill.
    private void checkAvailable(Account account, Order order, BigDecimal price) {
        Reservations reserved = Reservations.of(
                orders.findByAccountIdAndStatusOrderByCreatedAtDesc(account.getId(), OrderStatus.OPEN));
        if (order.getSide() == OrderSide.BUY) {
            BigDecimal cost = notional(price, order.getQuantity());
            BigDecimal buyingPower = account.getCash().subtract(reserved.cash());
            if (buyingPower.compareTo(cost) < 0) {
                throw new OrderRejectedException("insufficient cash: order needs " + cost + ", buying power is "
                        + buyingPower.max(BigDecimal.ZERO) + " after open orders");
            }
            long held = held(account.getId(), order.getSymbol());
            if (held + order.getQuantity() > Integer.MAX_VALUE) {
                throw new OrderRejectedException("position quantity exceeds supported maximum");
            }
        } else {
            long free = held(account.getId(), order.getSymbol()) - reserved.shares(order.getSymbol());
            if (free < order.getQuantity()) {
                throw new OrderRejectedException("insufficient shares: order sells " + order.getQuantity() + " "
                        + order.getSymbol() + ", " + Math.max(free, 0) + " not already on open orders");
            }
        }
    }

    private boolean canSettle(Account account, Order order, int shares, BigDecimal price) {
        return order.getSide() == OrderSide.BUY
                ? account.canAfford(notional(price, shares))
                : held(account.getId(), order.getSymbol()) >= shares;
    }

    // one side of one fill: cash, position, order and trade row move together
    private void settle(Account account, Order order, int shares, BigDecimal price, Long counterpartyOrderId) {
        BigDecimal notional = notional(price, shares);
        Position position = positions.findByAccountIdAndSymbol(account.getId(), order.getSymbol())
                .orElseGet(() -> new Position(account.getId(), order.getSymbol()));
        if (order.getSide() == OrderSide.BUY) {
            if (!account.canAfford(notional)) {
                throw new OrderRejectedException("insufficient cash: fill costs " + notional
                        + ", account has " + account.getCash());
            }
            account.debit(notional);
            position.buy(shares, price);
        } else {
            if (position.getQuantity() < shares) {
                throw new OrderRejectedException("insufficient shares: fill sells " + shares + " "
                        + order.getSymbol() + ", account holds " + position.getQuantity());
            }
            position.sell(shares, price);
            account.credit(notional);
        }
        order.fill(shares, price);
        positions.save(position);
        trades.save(new Trade(order, shares, price, counterpartyOrderId));
    }

    private int held(Long accountId, String symbol) {
        return positions.findByAccountIdAndSymbol(accountId, symbol).map(Position::getQuantity).orElse(0);
    }

    private void publish(Order order) {
        events.publishEvent(new OrderChanged(order.getAccountId(), order.getId(), order.getStatus(),
                order.getFilledQuantity(), order.getQuantity()));
    }

    private static BigDecimal notional(BigDecimal price, int shares) {
        return price.multiply(BigDecimal.valueOf(shares)).setScale(2, RoundingMode.HALF_UP);
    }
}
