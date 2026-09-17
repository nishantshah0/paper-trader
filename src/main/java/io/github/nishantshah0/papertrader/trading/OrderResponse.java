package io.github.nishantshah0.papertrader.trading;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(Long id, Long accountId, String symbol, OrderSide side, OrderType type, int quantity,
        BigDecimal limitPrice, OrderStatus status, Instant createdAt, Instant updatedAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getAccountId(), order.getSymbol(), order.getSide(),
                order.getType(), order.getQuantity(), order.getLimitPrice(), order.getStatus(),
                order.getCreatedAt(), order.getUpdatedAt());
    }
}
