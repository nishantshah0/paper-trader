package io.github.nishantshah0.papertrader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.nishantshah0.papertrader.quote.Quote;
import io.github.nishantshah0.papertrader.quote.QuoteCache;
import io.github.nishantshah0.papertrader.trading.MatchingEngine;
import io.github.nishantshah0.papertrader.trading.OrderService;
import io.github.nishantshah0.papertrader.trading.OrderSide;
import io.github.nishantshah0.papertrader.trading.OrderStatus;
import io.github.nishantshah0.papertrader.trading.OrderType;
import io.github.nishantshah0.papertrader.trading.PlaceOrderRequest;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;

@SpringBootTest(properties = {"papertrader.engine.enabled=true", "papertrader.feed.mode=static",
        "papertrader.starting-cash=1000.00", "papertrader.seed-quotes.AAPL=100.00"})
class LimitOrderFillTest extends ApiTestSupport {

    @Autowired OrderService service;
    @Autowired QuoteCache quotes;
    @Autowired MatchingEngine engine;
    @Autowired ApplicationEventPublisher events;

    @Test
    void restingBuyFillsOnItsOwnWhenThePriceDrops() throws Exception {
        var account = createAccount();
        var order = service.place(account.id(), limitBuy("90"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.OPEN);
        await().atMost(Duration.ofSeconds(5)).until(() -> engine.restingOrders("AAPL") > 0);

        publish("85");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(service.get(account.id(), order.getId()).getStatus()).isEqualTo(OrderStatus.FILLED));
        assertThat(portfolio(account).cash()).isEqualByComparingTo("830");
        assertThat(service.trades(account.id())).hasSize(1);
        publish("100");
    }

    @Test
    void aCancelledOrderNeverFillsEvenWhenThePriceLaterCrosses() throws Exception {
        var account = createAccount();
        var order = service.place(account.id(), limitBuy("80"));
        await().atMost(Duration.ofSeconds(5)).until(() -> engine.restingOrders("AAPL") > 0);

        service.cancel(account.id(), order.getId());
        publish("75");

        assertThat(service.get(account.id(), order.getId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(service.trades(account.id())).isEmpty();
        assertThat(portfolio(account).cash()).isEqualByComparingTo("1000");
        publish("100");
    }

    private void publish(String price) {
        // what MarketDataFeed does on every poll
        quotes.update(new Quote("AAPL", new BigDecimal(price), Instant.now()));
        events.publishEvent(quotes.require("AAPL"));
    }

    private static PlaceOrderRequest limitBuy(String limit) {
        return new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 2, new BigDecimal(limit));
    }
}
