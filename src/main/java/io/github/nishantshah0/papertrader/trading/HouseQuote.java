package io.github.nishantshah0.papertrader.trading;

import java.math.BigDecimal;
import java.math.RoundingMode;

// The simulated market maker. It quotes a bid and an ask either side of the feed price
// and takes any size at those prices, so a market order always fills. Users who post
// inside the spread get filled by each other first; whatever is left trades here.
// Prices round outward to four decimals, so the spread is never negative.
public record HouseQuote(BigDecimal bid, BigDecimal ask) {

    private static final BigDecimal BASIS_POINTS_BOTH_SIDES = BigDecimal.valueOf(20_000);

    public HouseQuote {
        if (bid.signum() <= 0 || bid.compareTo(ask) > 0) {
            throw new IllegalArgumentException("house bid " + bid + " must be positive and at most the ask " + ask);
        }
    }

    public static HouseQuote around(BigDecimal mid, int spreadBps) {
        if (spreadBps < 0 || spreadBps > 1_000) {
            throw new IllegalArgumentException("house spread must be between 0 and 1000 basis points");
        }
        BigDecimal half = mid.multiply(BigDecimal.valueOf(spreadBps)).divide(BASIS_POINTS_BOTH_SIDES);
        return new HouseQuote(mid.subtract(half).setScale(4, RoundingMode.FLOOR),
                mid.add(half).setScale(4, RoundingMode.CEILING));
    }

    // what an order on this side trades at against the house: buys lift the ask,
    // sells hit the bid
    public BigDecimal priceFor(OrderSide side) {
        return side == OrderSide.BUY ? ask : bid;
    }
}
