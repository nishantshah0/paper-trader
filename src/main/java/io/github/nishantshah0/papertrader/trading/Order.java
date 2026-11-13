package io.github.nishantshah0.papertrader.trading;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", length = 128, updatable = false)
    private String idempotencyKey;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(nullable = false, updatable = false, length = 10)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 4)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, updatable = false, length = 6)
    private OrderType type;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "limit_price", updatable = false, precision = 18, scale = 4)
    private BigDecimal limitPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 9)
    private OrderStatus status;

    @Column(name = "filled_quantity", nullable = false)
    private int filledQuantity;

    @Column(name = "avg_fill_price", precision = 18, scale = 4)
    private BigDecimal avgFillPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Order() {
    }

    private Order(Long accountId, String symbol, OrderSide side, OrderType type, int quantity, BigDecimal limitPrice) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
        this.status = OrderStatus.OPEN;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public static Order market(Long accountId, String symbol, OrderSide side, int quantity) {
        return new Order(accountId, symbol, side, OrderType.MARKET, quantity, null);
    }

    public static Order limit(Long accountId, String symbol, OrderSide side, int quantity, BigDecimal price) {
        return new Order(accountId, symbol, side, OrderType.LIMIT, quantity, price);
    }

    public boolean crosses(BigDecimal price) {
        return type == OrderType.MARKET || (side == OrderSide.BUY
                ? price.compareTo(limitPrice) <= 0 : price.compareTo(limitPrice) >= 0);
    }

    public void reject() {
        transition(OrderStatus.REJECTED);
    }

    public boolean isOpen() {
        return status == OrderStatus.OPEN;
    }

    public int remainingQuantity() {
        return quantity - filledQuantity;
    }

    // one execution of part or all of what is left. the order stays OPEN until nothing
    // remains, and the average price is weighted by the shares in each fill
    public void fill(int shares, BigDecimal price) {
        if (!isOpen() || shares <= 0 || shares > remainingQuantity()) {
            throw new IllegalStateException("order " + id + " cannot fill " + shares + " of "
                    + remainingQuantity() + " remaining (" + status + ")");
        }
        BigDecimal filledValue = avgFillPrice == null ? BigDecimal.ZERO
                : avgFillPrice.multiply(BigDecimal.valueOf(filledQuantity));
        filledQuantity += shares;
        avgFillPrice = filledValue.add(price.multiply(BigDecimal.valueOf(shares)))
                .divide(BigDecimal.valueOf(filledQuantity), 4, RoundingMode.HALF_UP);
        if (filledQuantity == quantity) {
            transition(OrderStatus.FILLED);
        } else {
            updatedAt = Instant.now();
        }
    }

    public void cancel() {
        transition(OrderStatus.CANCELLED);
    }

    private void transition(OrderStatus next) {
        if (!isOpen()) {
            throw new IllegalStateException("order " + id + " is " + status + ", cannot move to " + next);
        }
        status = next;
        updatedAt = Instant.now();
    }

    public void setIdempotencyKey(String key) { this.idempotencyKey = key; }

    public boolean sameRequest(PlaceOrderRequest request) {
        return symbol.equalsIgnoreCase(request.symbol()) && side == request.side()
                && type == request.typeOrMarket() && quantity == request.quantity()
                && (limitPrice == null ? request.limitPrice() == null
                    : request.limitPrice() != null && limitPrice.compareTo(request.limitPrice()) == 0);
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

    public OrderSide getSide() {
        return side;
    }

    public OrderType getType() {
        return type;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getLimitPrice() {
        return limitPrice;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public int getFilledQuantity() {
        return filledQuantity;
    }

    public BigDecimal getAvgFillPrice() {
        return avgFillPrice;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
