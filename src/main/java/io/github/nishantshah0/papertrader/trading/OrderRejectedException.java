package io.github.nishantshah0.papertrader.trading;

public class OrderRejectedException extends RuntimeException {

    public OrderRejectedException(String reason) {
        super(reason);
    }
}
