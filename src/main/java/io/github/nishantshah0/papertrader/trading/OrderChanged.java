package io.github.nishantshah0.papertrader.trading;

// published inside the transaction; listeners act on it after commit
public record OrderChanged(Long accountId, Long orderId, OrderStatus status, int filledQuantity, int quantity) {
}
