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

    public QuoteCache(PaperTraderProperties properties) {
        Instant now = Instant.now();
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
        quotes.put(quote.symbol(), quote);
    }

    public Set<String> symbols() {
        return Set.copyOf(quotes.keySet());
    }

    public List<Quote> all() {
        return quotes.values().stream().sorted(Comparator.comparing(Quote::symbol)).toList();
    }
}
