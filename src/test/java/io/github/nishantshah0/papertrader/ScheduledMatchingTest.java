package io.github.nishantshah0.papertrader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import io.github.nishantshah0.papertrader.quote.*;
import io.github.nishantshah0.papertrader.trading.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"papertrader.engine.enabled=true", "papertrader.engine.interval-ms=50",
        "papertrader.feed.mode=static", "papertrader.starting-cash=1000.00", "papertrader.seed-quotes.AAPL=100.00"})
class ScheduledMatchingTest extends ApiTestSupport {
    @Autowired OrderService service;
    @Autowired QuoteCache quotes;

    @Test void schedulerFillsRestingOrderAfterQuoteCrossesWithoutAnotherRequest() throws Exception {
        var account = createAccount();
        var order = service.place(account.id(), new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT,
                2, new BigDecimal("90")));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.OPEN);
        quotes.update(new Quote("AAPL", new BigDecimal("85"), Instant.now()));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(service.get(account.id(), order.getId()).getStatus()).isEqualTo(OrderStatus.FILLED));
        assertThat(portfolio(account).cash()).isEqualByComparingTo("830");
        assertThat(service.trades(account.id())).hasSize(1);
    }
}
