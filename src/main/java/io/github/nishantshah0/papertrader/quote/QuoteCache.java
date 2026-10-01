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

// latest quote per symbol. seeded from config so orders have a price before
// the live feed exists; the feed overwrites these as it polls
@Component
public class QuoteCache {

    private final Map<String, Quote> quotes = new ConcurrentHashMap<>();

    private final String mode;

    public QuoteCache(PaperTraderProperties properties,
            @org.springframework.beans.factory.annotation.Value("${papertrader.feed.mode:demo}") String mode) {
        this.mode = mode;
        Instant now = mode.equals("finnhub") ? Instant.EPOCH : Instant.now();
        properties.seedQuotes().forEach((symbol, price) -> {
            String key = symbol.toUpperCase(Locale.ROOT);
            quotes.put(key, new Quote(key, price, now));
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
        quotes.compute(quote.symbol(), (symbol, previous) -> previous == null || !quote.asOf().isBefore(previous.asOf()) ? quote : previous);
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
