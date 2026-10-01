package io.github.nishantshah0.papertrader;

import static org.assertj.core.api.Assertions.assertThat;
import io.github.nishantshah0.papertrader.quote.*;
import io.github.nishantshah0.papertrader.trading.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

class LimitOrderTest extends ApiTestSupport {
    @Autowired OrderService service;
    @Autowired QuoteCache quotes;
    @Autowired OrderRepository repository;

    void price(String value) { quotes.update(new Quote("AAPL", new BigDecimal(value), Instant.now())); }
    @AfterEach void resetPrice() { price("100.00"); }

    @Test void restingOrderFillsOnceAtBetterPrice() throws Exception {
        var a = createAccount();
        var o = read(post(orders(a), "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"quantity\":2,\"limitPrice\":90}"), OrderResponse.class);
        assertThat(o.status()).isEqualTo(OrderStatus.OPEN);
        service.match(a.id(), o.id());
        assertThat(service.get(a.id(), o.id()).isOpen()).isTrue();
        price("85");
        service.match(a.id(), o.id());
        service.match(a.id(), o.id());
        assertThat(portfolio(a).cash()).isEqualByComparingTo("830");
        assertThat(service.trades(a.id())).hasSize(1);
        assertThat(repository.findByStatusOrderByCreatedAtAscIdAsc(OrderStatus.OPEN))
                .noneMatch(order -> order.getId().equals(o.id()));
    }

    @Test void cancellationSurvivesLaterCrossing() throws Exception {
        var a = createAccount();
        var o = service.place(a.id(), new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 1, new BigDecimal("90")));
        service.cancel(a.id(), o.getId());
        price("80"); service.match(a.id(), o.getId());
        assertThat(service.get(a.id(), o.getId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(service.trades(a.id())).isEmpty();
    }

    @Test void unfundedRestingOrderRejectsWithoutDebiting() throws Exception {
        var a = createAccount();
        var o = service.place(a.id(), new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 10, new BigDecimal("90")));
        service.place(a.id(), new PlaceOrderRequest("AAPL", OrderSide.BUY, null, 10, null));
        price("90"); service.match(a.id(), o.getId());
        assertThat(service.get(a.id(), o.getId()).getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(portfolio(a).cash()).isEqualByComparingTo("0");
        assertThat(service.trades(a.id())).hasSize(1);
    }

    @Test void sellLimitWaitsForPriceAndRealizesProfit() throws Exception {
        var a = createAccount();
        service.place(a.id(), new PlaceOrderRequest("AAPL", OrderSide.BUY, null, 2, null));
        var o = service.place(a.id(), new PlaceOrderRequest("AAPL", OrderSide.SELL, OrderType.LIMIT, 2, new BigDecimal("110")));
        price("109"); service.match(a.id(), o.getId());
        assertThat(service.get(a.id(), o.getId()).isOpen()).isTrue();
        price("112"); service.match(a.id(), o.getId());
        assertThat(portfolio(a).cash()).isEqualByComparingTo("1024");
        assertThat(portfolio(a).realizedPnl()).isEqualByComparingTo("24");
    }

    @Test void priceMustMatchOrderType() throws Exception {
        var a = createAccount();
        assertThat(post(orders(a), "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"quantity\":1}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(post(orders(a), "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1,\"limitPrice\":90}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }
}
