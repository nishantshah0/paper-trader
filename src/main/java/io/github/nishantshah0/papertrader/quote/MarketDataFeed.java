package io.github.nishantshah0.papertrader.quote;

import io.github.nishantshah0.papertrader.PaperTraderProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.slf4j.LoggerFactory;

@Component
public class MarketDataFeed {
    private final QuoteCache quotes;
    private final PaperTraderProperties properties;
    private final ApplicationEventPublisher events;
    private final String apiKey;
    private final RestClient client;
    private long tick;

    MarketDataFeed(QuoteCache quotes, PaperTraderProperties properties, ApplicationEventPublisher events,
            @Value("${papertrader.feed.api-key:}") String apiKey) {
        this.quotes = quotes; this.properties = properties; this.events = events; this.apiKey = apiKey;
        if (!java.util.Set.of("demo", "static", "finnhub").contains(quotes.mode()))
            throw new IllegalArgumentException("feed mode must be demo, static, or finnhub");
        if (quotes.mode().equals("finnhub") && apiKey.isBlank())
            throw new IllegalArgumentException("FINNHUB_API_KEY is required for finnhub mode");
        var http = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = RestClient.builder().baseUrl("https://finnhub.io/api/v1").requestFactory(factory).build();
    }

    @Scheduled(fixedDelayString = "${papertrader.feed.interval-ms:15000}", initialDelayString = "${papertrader.feed.initial-delay-ms:3000}")
    public void poll() {
        if (quotes.mode().equals("static")) return;
        tick++;
        for (String symbol : quotes.symbols().stream().sorted().toList()) {
            try {
                Quote quote;
                if (quotes.mode().equals("demo")) {
                    double wave = Math.sin(tick * 0.35 + Math.floorMod(symbol.hashCode(), 20)) * 0.025;
                    BigDecimal price = properties.seedQuotes().get(symbol).multiply(BigDecimal.valueOf(1 + wave)).setScale(4, RoundingMode.HALF_UP);
                    quote = new Quote(symbol, price, Instant.now());
                } else {
                    Map<?, ?> response = client.get().uri(uri -> uri.path("/quote").queryParam("symbol", symbol).build())
                            .header("X-Finnhub-Token", apiKey).retrieve().body(Map.class);
                    if (response == null || response.get("c") == null || response.get("t") == null) continue;
                    quote = new Quote(symbol, new BigDecimal(response.get("c").toString()),
                            Instant.ofEpochSecond(((Number) response.get("t")).longValue()));
                }
                quotes.update(quote);
                events.publishEvent(quotes.require(symbol));
            } catch (RuntimeException e) {
                // Never log provider response bodies or credentials. Keep the last quote;
                // execution rejects old prices instead of silently using seed prices.
                LoggerFactory.getLogger(getClass()).warn("Quote refresh failed for {} ({})", symbol, e.getClass().getSimpleName());
            }
        }
    }
}
