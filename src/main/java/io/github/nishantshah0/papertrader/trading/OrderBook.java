package io.github.nishantshah0.papertrader.trading;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

// resting limit orders for one symbol. bids keep the highest limit first, asks the lowest,
// and orders at the same price keep arrival order, so a crossing quote drains the best
// levels in price-time priority. the id index makes cancels cheap. not thread safe by
// itself: the matching engine holds one lock per book around every call
public class OrderBook {

    private final String symbol;
    private final TreeMap<BigDecimal, ArrayDeque<RestingOrder>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<BigDecimal, ArrayDeque<RestingOrder>> asks = new TreeMap<>();
    private final Map<Long, RestingOrder> byId = new HashMap<>();

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    public void add(RestingOrder order) {
        if (!order.symbol().equals(symbol)) {
            throw new IllegalArgumentException(order.symbol() + " order offered to the " + symbol + " book");
        }
        if (byId.putIfAbsent(order.id(), order) != null) {
            return; // already resting, e.g. a rebuild racing a placement
        }
        levels(order.side()).computeIfAbsent(order.limitPrice(), price -> new ArrayDeque<>()).addLast(order);
    }

    public Optional<RestingOrder> remove(long orderId) {
        RestingOrder order = byId.remove(orderId);
        if (order == null) {
            return Optional.empty();
        }
        TreeMap<BigDecimal, ArrayDeque<RestingOrder>> levels = levels(order.side());
        ArrayDeque<RestingOrder> level = levels.get(order.limitPrice());
        level.remove(order);
        if (level.isEmpty()) {
            levels.remove(order.limitPrice());
        }
        return Optional.of(order);
    }

    // pops every order the price crosses: bids at or above it first, then asks at or below it
    public List<RestingOrder> crossing(BigDecimal price) {
        List<RestingOrder> crossed = new ArrayList<>();
        drain(bids, limit -> limit.compareTo(price) >= 0, crossed);
        drain(asks, limit -> limit.compareTo(price) <= 0, crossed);
        return crossed;
    }

    private void drain(TreeMap<BigDecimal, ArrayDeque<RestingOrder>> levels, Predicate<BigDecimal> crosses,
            List<RestingOrder> into) {
        Iterator<Map.Entry<BigDecimal, ArrayDeque<RestingOrder>>> it = levels.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BigDecimal, ArrayDeque<RestingOrder>> level = it.next();
            if (!crosses.test(level.getKey())) {
                break; // levels are sorted best first, so nothing past here crosses either
            }
            for (RestingOrder order : level.getValue()) {
                byId.remove(order.id());
                into.add(order);
            }
            it.remove();
        }
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

    private TreeMap<BigDecimal, ArrayDeque<RestingOrder>> levels(OrderSide side) {
        return side == OrderSide.BUY ? bids : asks;
    }
}
