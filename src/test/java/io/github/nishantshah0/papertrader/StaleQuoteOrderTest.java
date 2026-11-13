package io.github.nishantshah0.papertrader;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nishantshah0.papertrader.quote.Quote;
import io.github.nishantshah0.papertrader.quote.QuoteCache;
import io.github.nishantshah0.papertrader.trading.OrderResponse;
import io.github.nishantshah0.papertrader.trading.OrderService;
import io.github.nishantshah0.papertrader.trading.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

// Finnhub mode with the feed held back, so every quote starts as a seed with an epoch
// timestamp: the same position as outside market hours or just after startup. The
// tests never contact Finnhub. AAPL stays stale throughout; MSFT gets fresh quotes.
@SpringBootTest(properties = {
        "papertrader.engine.enabled=false",
        "papertrader.feed.mode=finnhub",
        "papertrader.feed.api-key=test-key-not-used",
        "papertrader.feed.initial-delay-ms=3600000",
        "papertrader.feed.interval-ms=3600000",
        "papertrader.starting-cash=1000.00",
        "papertrader.seed-quotes.AAPL=100.00",
        "papertrader.seed-quotes.MSFT=50.00"
})
class StaleQuoteOrderTest extends ApiTestSupport {

    @Autowired
    OrderService service;

    @Autowired
    QuoteCache quotes;

    @Test
    void ordersThatWouldFillNowAreRefusedOnAStaleQuote() throws Exception {
        var account = createAccount();

        var market = post(orders(account), "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}");
        assertThat(market).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(detail(market)).contains("stale");

        // a buy limit above the last price is marketable, so it would execute now
        var marketableLimit = post(orders(account),
                "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"quantity\":1,\"limitPrice\":110}");
        assertThat(marketableLimit).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);

        assertThat(service.list(account.id(), null)).isEmpty();
        assertThat(portfolio(account).cash()).isEqualByComparingTo("1000");
    }

    @Test
    void limitOrderRestsOnAStaleQuoteAndFillsOnTheFirstFreshOne() throws Exception {
        var account = createAccount();

        var placed = post(orders(account),
                "{\"symbol\":\"MSFT\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"quantity\":2,\"limitPrice\":45}");
        assertThat(placed).hasStatus(HttpStatus.CREATED);
        var order = read(placed, OrderResponse.class);
        assertThat(order.status()).isEqualTo(OrderStatus.OPEN);
        assertThat(portfolio(account).reservedCash()).isEqualByComparingTo("90");

        // crosses the limit but is three minutes old, so it still cannot execute
        quotes.update(new Quote("MSFT", new BigDecimal("44.00"), Instant.now().minusSeconds(180)));
        assertThat(service.match(account.id(), order.id())).isEqualTo(OrderStatus.OPEN);

        quotes.update(new Quote("MSFT", new BigDecimal("44.00"), Instant.now()));
        assertThat(service.match(account.id(), order.id())).isEqualTo(OrderStatus.FILLED);
        assertThat(service.trades(account.id())).hasSize(1);
        assertThat(portfolio(account).cash()).isEqualByComparingTo("912");
        assertThat(portfolio(account).reservedCash()).isEqualByComparingTo("0");
    }
}
