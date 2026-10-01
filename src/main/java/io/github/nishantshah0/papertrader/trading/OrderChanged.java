package io.github.nishantshah0.papertrader.trading;
public record OrderChanged(Long accountId, Long orderId, OrderStatus status) {}
