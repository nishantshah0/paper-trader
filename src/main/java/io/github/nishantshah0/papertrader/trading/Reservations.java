package io.github.nishantshah0.papertrader.trading;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// What an account's open limit orders have already claimed: cash for buys at their
// limit, shares for sells. Worked out from the OPEN rows rather than stored, so there
// is no second balance to keep in sync. Callers read the orders under the account
// row lock, which is what keeps the sum current.
public final class Reservations {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final BigDecimal cash;
    private final Map<String, Integer> shares;

    private Reservations(BigDecimal cash, Map<String, Integer> shares) {
        this.cash = cash;
        this.shares = shares;
    }

    public static Reservations of(List<Order> openOrders) {
        BigDecimal cash = ZERO;
        Map<String, Integer> shares = new HashMap<>();
        for (Order order : openOrders) {
            if (!order.isOpen() || order.getType() != OrderType.LIMIT) {
                continue;
            }
            if (order.getSide() == OrderSide.BUY) {
                // rounded per order, the same way a fill prices it
                cash = cash.add(order.getLimitPrice().multiply(BigDecimal.valueOf(order.getQuantity()))
                        .setScale(2, RoundingMode.HALF_UP));
            } else {
                shares.merge(order.getSymbol(), order.getQuantity(), Integer::sum);
            }
        }
        return new Reservations(cash, shares);
    }

    public BigDecimal cash() {
        return cash;
    }

    public int shares(String symbol) {
        return shares.getOrDefault(symbol, 0);
    }
}
