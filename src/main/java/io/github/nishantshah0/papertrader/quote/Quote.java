package io.github.nishantshah0.papertrader.quote;

import java.math.BigDecimal;
import java.time.Instant;

public record Quote(String symbol, BigDecimal price, Instant asOf) {
}
