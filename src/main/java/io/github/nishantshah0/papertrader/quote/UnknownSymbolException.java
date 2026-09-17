package io.github.nishantshah0.papertrader.quote;

public class UnknownSymbolException extends RuntimeException {

    public UnknownSymbolException(String symbol) {
        super("unknown symbol " + symbol);
    }
}
