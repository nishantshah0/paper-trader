package io.github.nishantshah0.papertrader.quote;

import io.github.nishantshah0.papertrader.PaperTraderProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MarketDataFeed {

    private static final Logger log = LoggerFactory.getLogger(MarketDataFeed.class);

    private final QuoteCache quotes;
    private final PaperTraderProperties properties;
    private final ApplicationEventPublisher events;
    private final String apiKey;
    private final RestClient client;
    private long tick;

    MarketDataFeed(QuoteCache quotes, PaperTraderProperties properties, ApplicationEventPublisher events,
            @Value("${papertrader.feed.api-key:}") String apiKey) {
        this.quotes = quotes;
        this.properties = properties;
        this.events = events;
        this.apiKey = apiKey;
        if (!Set.of("demo", "static", "finnhub").contains(quotes.mode())) {
            throw new IllegalArgumentException("feed mode must be demo, static, or finnhub");
        }
        if (quotes.mode().equals("finnhub") && apiKey.isBlank()) {
            throw new IllegalArgumentException("FINNHUB_API_KEY is required for finnhub mode");
        }
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = RestClient.builder().baseUrl("https://finnhub.io/api/v1").requestFactory(factory).build();
    }

    @Scheduled(fixedDelayString = "${papertrader.feed.interval-ms:15000}",
            initialDelayString = "${papertrader.feed.initial-delay-ms:3000}")
    public void poll() {
        if (quotes.mode().equals("static")) {
            return;
        }
        tick++;
        for (String symbol : quotes.symbols().stream().sorted().toList()) {
            try {
                Quote quote = quotes.mode().equals("demo") ? demoQuote(symbol) : finnhubQuote(symbol);
                if (quote == null) {
                    continue;
                }
                quotes.update(quote);
                events.publishEvent(quotes.require(symbol));
            } catch (RuntimeException e) {
                // never log provider response bodies or credentials. the last quote stays,
                // and execution refuses stale prices rather than falling back to seed prices
                log.warn("Quote refresh failed for {} ({})", symbol, e.getClass().getSimpleName());
            }
        }
    }

    // a bounded sine wave around the seed price, phase-shifted per symbol
    private Quote demoQuote(String symbol) {
        double wave = Math.sin(tick * 0.35 + Math.floorMod(symbol.hashCode(), 20)) * 0.025;
        BigDecimal price = properties.seedQuotes().get(symbol)
                .multiply(BigDecimal.valueOf(1 + wave))
                .setScale(4, RoundingMode.HALF_UP);
        return new Quote(symbol, price, Instant.now());
    }

    private Quote finnhubQuote(String symbol) {
        Map<?, ?> response = client.get()
                .uri(uri -> uri.path("/quote").queryParam("symbol", symbol).build())
                .header("X-Finnhub-Token", apiKey)
                .retrieve()
                .body(Map.class);
        if (response == null || response.get("c") == null || response.get("t") == null) {
            return null;
        }
        return new Quote(symbol, new BigDecimal(response.get("c").toString()),
                Instant.ofEpochSecond(((Number) response.get("t")).longValue()));
    }
}
