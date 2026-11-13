package io.github.nishantshah0.papertrader.trading;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

// Resting limit orders for one symbol. Bids keep the highest limit first, asks the lowest,
// and each price level keeps arrival order, so matching walks the book in price-time
// priority. A level is a LinkedHashMap keyed by order ID: cancels are O(1) within the
// level, and a partial fill replaces its entry without losing its place in the queue.
//
// Not thread safe, and does not need to be: each book is owned by one matcher thread.
// Planning methods only read; the book changes once the fills they planned have committed.
public class OrderBook {

    // shares to take from one resting order, at its limit price
    public record PlannedFill(RestingOrder maker, int quantity) {
        public BigDecimal price() {
            return maker.limitPrice();
        }
    }

    public record Level(BigDecimal price, int quantity, int orders) {
    }

    private final String symbol;
    private final TreeMap<BigDecimal, LinkedHashMap<Long, RestingOrder>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<BigDecimal, LinkedHashMap<Long, RestingOrder>> asks = new TreeMap<>();
    private final Map<Long, RestingOrder> byId = new HashMap<>();

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    public void add(RestingOrder order) {
        if (!order.symbol().equals(symbol)) {
            throw new IllegalArgumentException(order.symbol() + " order offered to the " + symbol + " book");
        }
        if (byId.putIfAbsent(order.id(), order) != null) {
            return; // already resting
        }
        levels(order.side()).computeIfAbsent(order.limitPrice(), price -> new LinkedHashMap<>()).put(order.id(), order);
    }

    public Optional<RestingOrder> remove(long orderId) {
        RestingOrder order = byId.remove(orderId);
        if (order == null) {
            return Optional.empty();
        }
        var levels = levels(order.side());
        var level = levels.get(order.limitPrice());
        level.remove(orderId);
        if (level.isEmpty()) {
            levels.remove(order.limitPrice());
        }
        return Optional.of(order);
    }

    // takes filled shares off a resting order, which leaves the book once nothing remains
    public void reduce(long orderId, int shares) {
        RestingOrder order = byId.get(orderId);
        if (order == null || shares > order.remaining()) {
            throw new IllegalStateException("cannot take " + shares + " shares from order " + orderId);
        }
        if (shares == order.remaining()) {
            remove(orderId);
            return;
        }
        RestingOrder rest = order.withRemaining(order.remaining() - shares);
        byId.put(orderId, rest);
        levels(order.side()).get(order.limitPrice()).put(orderId, rest); // keeps its place in the queue
    }

    // The resting orders an incoming order would trade with, best price first and oldest
    // first within a price. It stops at its own limit (null for a market order), and at
    // the first level the house beats: the house takes any size, so no taker should pay
    // a user more than the house asks. At an equal price the user goes first.
    public List<PlannedFill> match(OrderSide side, BigDecimal limit, int quantity, long accountId,
            HouseQuote house) {
        BigDecimal housePrice = house == null ? null : house.priceFor(side);
        List<PlannedFill> fills = new ArrayList<>();
        int remaining = quantity;
        for (var level : levels(opposite(side)).entrySet()) {
            BigDecimal price = level.getKey();
            if ((limit != null && !acceptable(side, price, limit))
                    || (housePrice != null && !acceptable(side, price, housePrice))) {
                break;
            }
            for (RestingOrder maker : level.getValue().values()) {
                if (maker.accountId() == accountId) {
                    throw new OrderRejectedException("order would trade with your own resting order #" + maker.id()
                            + "; cancel it first");
                }
                int shares = Math.min(remaining, maker.remaining());
                fills.add(new PlannedFill(maker, shares));
                remaining -= shares;
                if (remaining == 0) {
                    return fills;
                }
            }
        }
        return fills;
    }

    // resting orders the house now trades with: bids at or above its ask, asks at or
    // below its bid, best first
    public List<RestingOrder> crossedBy(HouseQuote house) {
        List<RestingOrder> crossed = new ArrayList<>();
        collect(bids, house.ask(), crossed);
        collect(asks, house.bid(), crossed);
        return crossed;
    }

    private static void collect(TreeMap<BigDecimal, LinkedHashMap<Long, RestingOrder>> levels, BigDecimal price,
            List<RestingOrder> into) {
        for (var level : levels.values()) {
            RestingOrder first = level.values().iterator().next();
            if (!first.crosses(price)) {
                break; // levels are sorted best first, so nothing past here crosses either
            }
            into.addAll(level.values());
        }
    }

    // aggregated price levels for display, best first
    public List<Level> depth(OrderSide side, int maxLevels) {
        return levels(side).entrySet().stream()
                .limit(maxLevels)
                .map(level -> new Level(level.getKey(),
                        level.getValue().values().stream().mapToInt(RestingOrder::remaining).sum(),
                        level.getValue().size()))
                .toList();
    }

    public void clear() {
        bids.clear();
        asks.clear();
        byId.clear();
    }

    public Optional<RestingOrder> get(long orderId) {
        return Optional.ofNullable(byId.get(orderId));
    }

    public boolean contains(long orderId) {
        return byId.containsKey(orderId);
    }

    public int size() {
        return byId.size();
    }

    public Optional<BigDecimal> bestBid() {
        return bids.isEmpty() ? Optional.empty() : Optional.of(bids.firstKey());
    }

    public Optional<BigDecimal> bestAsk() {
        return asks.isEmpty() ? Optional.empty() : Optional.of(asks.firstKey());
    }

    public String symbol() {
        return symbol;
    }

    // a buyer accepts prices at or below the bound, a seller at or above it
    private static boolean acceptable(OrderSide side, BigDecimal price, BigDecimal bound) {
        int cmp = price.compareTo(bound);
        return side == OrderSide.BUY ? cmp <= 0 : cmp >= 0;
    }

    private static OrderSide opposite(OrderSide side) {
        return side == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;
    }

    private TreeMap<BigDecimal, LinkedHashMap<Long, RestingOrder>> levels(OrderSide side) {
        return side == OrderSide.BUY ? bids : asks;
    }
}
