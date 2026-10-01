package io.github.nishantshah0.papertrader.quote;

import io.github.nishantshah0.papertrader.PaperTraderProperties;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

// Latest quote and a bounded history of observed prices for the terminal chart.
@Component
public class QuoteCache {

    private final Map<String, Quote> quotes = new ConcurrentHashMap<>();
    private final Map<String, List<Quote>> history = new ConcurrentHashMap<>();
    private static final int HISTORY_LIMIT = 240;

    private final String mode;

    public QuoteCache(PaperTraderProperties properties,
            @org.springframework.beans.factory.annotation.Value("${papertrader.feed.mode:demo}") String mode) {
        this.mode = mode;
        Instant now = mode.equals("finnhub") ? Instant.EPOCH : Instant.now();
        properties.seedQuotes().forEach((symbol, price) -> {
            String key = symbol.toUpperCase(Locale.ROOT);
            Quote seed = new Quote(key, price, now);
            quotes.put(key, seed);
            history.put(key, mode.equals("finnhub") ? List.of() : List.of(seed));
        });
    }

    public Optional<Quote> get(String symbol) {
        return Optional.ofNullable(quotes.get(symbol));
    }

    public Quote require(String symbol) {
        return get(symbol).orElseThrow(() -> new UnknownSymbolException(symbol));
    }

    public void update(Quote quote) {
        if (quote.price() == null || quote.price().signum() <= 0 || quote.price().compareTo(new java.math.BigDecimal("9999999999")) > 0
                || quote.asOf() == null || quote.asOf().isAfter(Instant.now().plusSeconds(30))) {
            throw new IllegalArgumentException("invalid quote");
        }
        quotes.compute(quote.symbol(), (symbol, previous) -> {
            if (previous != null && quote.asOf().isBefore(previous.asOf())) return previous;
            var samples = new java.util.ArrayList<>(history.getOrDefault(symbol, List.of()));
            if (!samples.isEmpty() && samples.getLast().asOf().equals(quote.asOf())) samples.removeLast();
            samples.add(quote);
            history.put(symbol, List.copyOf(samples.subList(Math.max(0, samples.size() - HISTORY_LIMIT), samples.size())));
            return quote;
        });
    }

    public Map<String, List<Quote>> history() {
        return Map.copyOf(history);
    }

    public boolean isTradable(Quote quote) {
        return !mode.equals("finnhub") || quote.asOf().isAfter(Instant.now().minusSeconds(120));
    }

    public void requireTradable(Quote quote) {
        if (!isTradable(quote)) throw new io.github.nishantshah0.papertrader.trading.OrderRejectedException("quote is stale; wait for a fresh market quote");
    }

    public String mode() { return mode; }

    public Set<String> symbols() {
        return Set.copyOf(quotes.keySet());
    }

    public List<Quote> all() {
        return quotes.values().stream().sorted(Comparator.comparing(Quote::symbol)).toList();
    }
}
