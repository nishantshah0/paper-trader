package io.github.nishantshah0.papertrader.trading;

import java.math.BigDecimal;
import java.time.Instant;

// counterparty is USER for a trade with another account's order and HOUSE otherwise;
// the other account is not exposed
public record TradeResponse(Long id, Long orderId, String symbol, OrderSide side, int quantity, BigDecimal price,
        BigDecimal notional, Counterparty counterparty, Instant executedAt) {

    public enum Counterparty {
        USER, HOUSE
    }

    public static TradeResponse from(Trade trade) {
        return new TradeResponse(trade.getId(), trade.getOrderId(), trade.getSymbol(), trade.getSide(),
                trade.getQuantity(), trade.getPrice(), trade.notional(),
                trade.getCounterpartyOrderId() == null ? Counterparty.HOUSE : Counterparty.USER,
                trade.getExecutedAt());
    }
}
