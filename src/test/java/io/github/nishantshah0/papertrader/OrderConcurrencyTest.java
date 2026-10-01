package io.github.nishantshah0.papertrader;

import static org.assertj.core.api.Assertions.*;
import io.github.nishantshah0.papertrader.trading.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OrderConcurrencyTest extends ApiTestSupport {
    @Autowired OrderService service;
    private PlaceOrderRequest buy(int quantity) { return new PlaceOrderRequest("AAPL", OrderSide.BUY, null, quantity, null); }

    @Test void concurrentRetriesCreateOnlyOneTrade() throws Exception {
        var a = createAccount();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            var first = pool.submit(() -> { start.await(); return service.place(a.id(), buy(7), "same-request").getId(); });
            var second = pool.submit(() -> { start.await(); return service.place(a.id(), buy(7), "same-request").getId(); });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(second.get(10, TimeUnit.SECONDS));
        }
        assertThat(service.trades(a.id())).hasSize(1);
        assertThat(portfolio(a).cash()).isEqualByComparingTo("300");
        assertThatThrownBy(() -> service.place(a.id(), buy(8), "same-request")).isInstanceOf(OrderStateException.class);
    }

    @Test void concurrentOrdersCannotSpendTheSameCash() throws Exception {
        var a = createAccount();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            Callable<Boolean> task = () -> {
                start.await();
                try { service.place(a.id(), buy(7)); return true; }
                catch (OrderRejectedException expected) { return false; }
            };
            var first = pool.submit(task); var second = pool.submit(task); start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS) ^ second.get(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(service.trades(a.id())).hasSize(1);
        assertThat(portfolio(a).cash()).isEqualByComparingTo("300");
    }

    @Test void cancellationRacingWithMatchHasOnlyOneOutcome() throws Exception {
        var a = createAccount();
        var order = service.place(a.id(), new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 1, new java.math.BigDecimal("90")));
        var quotes = this.quotesForTest;
        quotes.update(new io.github.nishantshah0.papertrader.quote.Quote("AAPL", new java.math.BigDecimal("80"), java.time.Instant.now()));
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            var match = pool.submit(() -> { start.await(); service.match(a.id(), order.getId()); return true; });
            var cancel = pool.submit(() -> { start.await(); try { service.cancel(a.id(), order.getId()); } catch (OrderStateException expected) {} return true; });
            start.countDown(); match.get(10, TimeUnit.SECONDS); cancel.get(10, TimeUnit.SECONDS);
            var status = service.get(a.id(), order.getId()).getStatus();
            assertThat(status).isIn(OrderStatus.FILLED, OrderStatus.CANCELLED);
            assertThat(service.trades(a.id())).hasSize(status == OrderStatus.FILLED ? 1 : 0);
            assertThat(portfolio(a).cash()).isEqualByComparingTo(status == OrderStatus.FILLED ? "920" : "1000");
        } finally {
            quotes.update(new io.github.nishantshah0.papertrader.quote.Quote("AAPL", new java.math.BigDecimal("100"), java.time.Instant.now()));
        }
    }
    @Autowired io.github.nishantshah0.papertrader.quote.QuoteCache quotesForTest;
}
