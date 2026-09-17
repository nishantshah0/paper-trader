package io.github.nishantshah0.papertrader.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true, updatable = false)
    private User user;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal cash;

    @Column(name = "starting_cash", nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal startingCash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
    }

    public Account(User user, BigDecimal startingCash) {
        this.user = user;
        this.startingCash = startingCash.setScale(2, RoundingMode.HALF_UP);
        this.cash = this.startingCash;
        this.createdAt = Instant.now();
    }

    public boolean canAfford(BigDecimal amount) {
        return cash.compareTo(amount) >= 0;
    }

    public void debit(BigDecimal amount) {
        if (!canAfford(amount)) {
            throw new IllegalStateException("cash would go negative");
        }
        cash = cash.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        cash = cash.add(amount);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public BigDecimal getCash() {
        return cash;
    }

    public BigDecimal getStartingCash() {
        return startingCash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
