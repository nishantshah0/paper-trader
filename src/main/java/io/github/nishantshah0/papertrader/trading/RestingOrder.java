package io.github.nishantshah0.papertrader.trading;

import java.math.BigDecimal;

// what the order book keeps for a resting limit order. a snapshot, not the entity:
// the engine reloads the row inside the fill transaction anyway
public record RestingOrder(long id, long accountId, String symbol, OrderSide side, BigDecimal limitPrice, int quantity) {

    public static RestingOrder of(Order order) {
        if (order.getType() != OrderType.LIMIT || order.getId() == null) {
            throw new IllegalArgumentException("only persisted limit orders can rest in the book");
        }
        return new RestingOrder(order.getId(), order.getAccountId(), order.getSymbol(), order.getSide(),
                order.getLimitPrice(), order.getQuantity());
    }

    // a bid is crossed when the market trades at or below its limit, an ask at or above
    public boolean crosses(BigDecimal price) {
        int cmp = limitPrice.compareTo(price);
        return side == OrderSide.BUY ? cmp >= 0 : cmp <= 0;
    }
}
