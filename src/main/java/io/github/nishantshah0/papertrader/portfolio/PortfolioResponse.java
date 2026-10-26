package io.github.nishantshah0.papertrader.portfolio;

import java.math.BigDecimal;
import java.util.List;

// buyingPower is cash minus what open buy limits hold; availableQuantity is shares not
// already on an open sell limit
public record PortfolioResponse(Long accountId, String username, BigDecimal cash, BigDecimal reservedCash,
        BigDecimal buyingPower, BigDecimal startingCash, BigDecimal marketValue, BigDecimal totalValue,
        BigDecimal unrealizedPnl, BigDecimal realizedPnl, BigDecimal totalPnl, List<PositionView> positions) {

    public record PositionView(String symbol, int quantity, int availableQuantity, BigDecimal avgCost,
            BigDecimal lastPrice, BigDecimal marketValue, BigDecimal unrealizedPnl, BigDecimal realizedPnl) {
    }
}
