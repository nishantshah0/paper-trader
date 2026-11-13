package io.github.nishantshah0.papertrader.trading;

public class EngineBusyException extends RuntimeException {
    public EngineBusyException(String symbol) {
        super("the " + symbol + " matcher did not respond in time; try again");
    }
}
