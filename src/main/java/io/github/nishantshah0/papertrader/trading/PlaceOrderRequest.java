package io.github.nishantshah0.papertrader.trading;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PlaceOrderRequest(
        @NotBlank @Size(max = 10) String symbol,
        @NotNull OrderSide side,
        OrderType type,
        @NotNull @Positive @Max(1_000_000) Integer quantity,
        @Positive BigDecimal limitPrice) {

    public OrderType typeOrMarket() {
        return type == null ? OrderType.MARKET : type;
    }
}
