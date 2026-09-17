package io.github.nishantshah0.papertrader.trading;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

// long only: quantity never goes below zero and avg_cost is the cost basis of what is held
@Entity
@Table(name = "positions")
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(nullable = false, updatable = false, length = 10)
    private String symbol;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "avg_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal avgCost;

    @Column(name = "realized_pnl", nullable = false, precision = 18, scale = 2)
    private BigDecimal realizedPnl;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Position() {
    }

    public Position(Long accountId, String symbol) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.quantity = 0;
        this.avgCost = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        this.realizedPnl = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.updatedAt = Instant.now();
    }

    public void buy(int qty, BigDecimal price) {
        int newQuantity = quantity + qty;
        BigDecimal costBasis = avgCost.multiply(BigDecimal.valueOf(quantity))
                .add(price.multiply(BigDecimal.valueOf(qty)));
        avgCost = costBasis.divide(BigDecimal.valueOf(newQuantity), 4, RoundingMode.HALF_UP);
        quantity = newQuantity;
        updatedAt = Instant.now();
    }

    public void sell(int qty, BigDecimal price) {
        if (qty > quantity) {
            throw new IllegalArgumentException("cannot sell " + qty + " " + symbol + ", only " + quantity + " held");
        }
        realizedPnl = realizedPnl.add(price.subtract(avgCost).multiply(BigDecimal.valueOf(qty)))
                .setScale(2, RoundingMode.HALF_UP);
        quantity -= qty;
        updatedAt = Instant.now();
    }

    public BigDecimal marketValue(BigDecimal lastPrice) {
        return lastPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal unrealizedPnl(BigDecimal lastPrice) {
        return lastPrice.subtract(avgCost).multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getAvgCost() {
        return avgCost;
    }

    public BigDecimal getRealizedPnl() {
        return realizedPnl;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
