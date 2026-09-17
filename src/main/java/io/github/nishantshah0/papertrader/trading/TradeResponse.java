package io.github.nishantshah0.papertrader.trading;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeResponse(Long id, Long orderId, String symbol, OrderSide side, int quantity, BigDecimal price,
        BigDecimal notional, Instant executedAt) {

    public static TradeResponse from(Trade trade) {
        return new TradeResponse(trade.getId(), trade.getOrderId(), trade.getSymbol(), trade.getSide(),
                trade.getQuantity(), trade.getPrice(), trade.notional(), trade.getExecutedAt());
    }
}
