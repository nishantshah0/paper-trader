package io.github.nishantshah0.papertrader.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.nishantshah0.papertrader.quote.Quote;
import io.github.nishantshah0.papertrader.quote.QuoteCache;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MatchingEngineTest {

    private final OrderRepository repository = mock(OrderRepository.class);
    private final OrderService service = mock(OrderService.class);
    private final QuoteCache quotes = mock(QuoteCache.class);
    private final Order bid90 = limit(11L, 1L, OrderSide.BUY, "90");
    private final Order bid80 = limit(12L, 2L, OrderSide.BUY, "80");
    private MatchingEngine engine;

    @BeforeEach
    void rebuildFromOpenOrders() {
        when(repository.findByStatusOrderByCreatedAtAscIdAsc(OrderStatus.OPEN)).thenReturn(List.of(bid90, bid80));
        when(quotes.symbols()).thenReturn(Set.of());
        engine = new MatchingEngine(repository, service, quotes);
        engine.rebuild();
    }

    @Test
    void startupRestsEveryOpenLimitOrder() {
        assertThat(engine.restingOrders("AAPL")).isEqualTo(2);
    }

    @Test
    void aQuoteMatchesOnlyTheOrdersItCrosses() {
        when(service.match(1L, 11L)).thenReturn(OrderStatus.FILLED);

        engine.onQuote(quote("85"));

        verify(service).match(1L, 11L);
        verify(service, never()).match(2L, 12L);
        assertThat(engine.restingOrders("AAPL")).isEqualTo(1);
    }

    @Test
    void anOrderThatFailsToMatchGoesBackInTheBook() {
        when(service.match(1L, 11L)).thenThrow(new IllegalStateException("database hiccup"));

        engine.onQuote(quote("85"));

        assertThat(engine.restingOrders("AAPL")).isEqualTo(2);
    }

    @Test
    void anOrderStillOpenAfterMatchingGoesBackInTheBook() {
        when(service.match(1L, 11L)).thenReturn(OrderStatus.OPEN);

        engine.onQuote(quote("85"));

        assertThat(engine.restingOrders("AAPL")).isEqualTo(2);
    }

    @Test
    void cancelledAndFilledOrdersLeaveTheBook() {
        engine.onOrderChanged(new OrderChanged(1L, 11L, OrderStatus.CANCELLED));
        engine.onOrderChanged(new OrderChanged(2L, 12L, OrderStatus.FILLED));

        assertThat(engine.restingOrders("AAPL")).isZero();
    }

    @Test
    void aNewRestingOrderIsCheckedAgainstTheCurrentQuote() {
        Order bid95 = limit(13L, 3L, OrderSide.BUY, "95");
        when(repository.findById(13L)).thenReturn(Optional.of(bid95));
        when(quotes.get("AAPL")).thenReturn(Optional.of(quote("94")));
        when(service.match(3L, 13L)).thenReturn(OrderStatus.FILLED);

        engine.onOrderChanged(new OrderChanged(3L, 13L, OrderStatus.OPEN));

        verify(service).match(3L, 13L);
        verify(service, never()).match(1L, 11L);
    }

    private static Order limit(Long id, Long accountId, OrderSide side, String price) {
        Order order = Order.limit(accountId, "AAPL", side, 1, new BigDecimal(price));
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    private static Quote quote(String price) {
        return new Quote("AAPL", new BigDecimal(price), Instant.now());
    }
}
