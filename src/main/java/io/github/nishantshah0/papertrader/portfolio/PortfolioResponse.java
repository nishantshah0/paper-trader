package io.github.nishantshah0.papertrader.portfolio;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioResponse(Long accountId, String username, BigDecimal cash, BigDecimal startingCash,
        BigDecimal marketValue, BigDecimal totalValue, BigDecimal unrealizedPnl, BigDecimal realizedPnl,
        BigDecimal totalPnl, List<PositionView> positions) {

    public record PositionView(String symbol, int quantity, BigDecimal avgCost, BigDecimal lastPrice,
            BigDecimal marketValue, BigDecimal unrealizedPnl, BigDecimal realizedPnl) {
    }
}
