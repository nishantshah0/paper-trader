package io.github.nishantshah0.papertrader.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.nishantshah0.papertrader.quote.Quote;
import io.github.nishantshah0.papertrader.quote.QuoteCache;
import io.github.nishantshah0.papertrader.trading.OrderExecution.BookChange;
import io.github.nishantshah0.papertrader.trading.OrderExecution.BookOutOfSyncException;
import io.github.nishantshah0.papertrader.trading.OrderExecution.Result;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// The engine with its database side mocked out: what reaches the book, and when.
class MatchingEngineTest {

    private final OrderRepository repository = mock(OrderRepository.class);
    private final OrderExecution execution = mock(OrderExecution.class);
    private final QuoteCache quotes = mock(QuoteCache.class);
    private final Order bid90 = limit(11L, 1L, "90");
    private final Order bid80 = limit(12L, 2L, "80");
    private final PlaceOrderRequest request =
            new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 1, new BigDecimal("70"));
    private MatchingEngine engine;

    @BeforeEach
    void rebuildFromOpenOrders() {
        when(repository.findByStatusOrderByCreatedAtAscIdAsc(OrderStatus.OPEN)).thenReturn(List.of(bid90, bid80));
        when(repository.findBySymbolAndStatusOrderByCreatedAtAscIdAsc("AAPL", OrderStatus.OPEN))
                .thenReturn(List.of(bid90, bid80));
        when(quotes.symbols()).thenReturn(Set.of("AAPL"));
        when(quotes.get("AAPL")).thenReturn(Optional.empty());
        engine = new MatchingEngine(repository, execution, quotes, 0);
        engine.rebuild();
    }

    @AfterEach
    void stopMatcherThreads() {
        engine.stop();
    }

    @Test
    void startupRestsEveryOpenLimitOrder() {
        assertThat(engine.restingOrders("AAPL")).isEqualTo(2);
    }

    @Test
    void aQuoteFillsOnlyTheOrdersTheHouseCrosses() {
        quoteAt("85");
        when(execution.fillAgainstHouse(argThat(order -> order.id() == 11L), any()))
                .thenReturn(new Result(bid90, List.of(new BookChange.Remove(11L))));

        engine.onQuote(new Quote("AAPL", new BigDecimal("85"), Instant.now()));

        assertThat(engine.restingOrders("AAPL")).isEqualTo(1); // queued behind the sweep
        verify(execution, never()).fillAgainstHouse(argThat(order -> order.id() == 12L), any());
    }

    @Test
    void anOrderThatFailsToFillStaysInTheBook() {
        quoteAt("85");
        when(execution.fillAgainstHouse(any(), any())).thenThrow(new IllegalStateException("database hiccup"));

        engine.onQuote(new Quote("AAPL", new BigDecimal("85"), Instant.now()));

        assertThat(engine.restingOrders("AAPL")).isEqualTo(2);
    }

    @Test
    void aCommittedPlacementUpdatesTheBookFromWhatItReports() {
        Order bid70 = limit(13L, 3L, "70");
        when(execution.place(any(), any(), any(), any(), any(), any())).thenReturn(new Result(bid70, List.of(
                new BookChange.Reduce(11L, 1), new BookChange.Rest(RestingOrder.of(bid70)))));

        assertThat(engine.place(3L, request, null, "AAPL")).isSameAs(bid70);

        assertThat(engine.restingOrders("AAPL")).isEqualTo(2); // 11 filled and left, 13 joined
        assertThat(engine.book("AAPL", 5).bids()).extracting(OrderBook.Level::price)
                .containsExactly(new BigDecimal("80"), new BigDecimal("70"));
    }

    @Test
    void aRejectedPlacementLeavesTheBookAloneAndReachesTheCaller() {
        when(execution.place(any(), any(), any(), any(), any(), any()))
                .thenThrow(new OrderRejectedException("insufficient cash"));

        assertThatThrownBy(() -> engine.place(3L, request, null, "AAPL"))
                .isInstanceOf(OrderRejectedException.class)
                .hasMessage("insufficient cash");
        assertThat(engine.restingOrders("AAPL")).isEqualTo(2);
    }

    @Test
    void aBookOutOfSyncIsReloadedFromTheDatabaseAndThePlacementRetried() {
        Order bid70 = limit(13L, 3L, "70");
        when(execution.place(any(), any(), any(), any(), any(), any()))
                .thenThrow(new BookOutOfSyncException("order 11 no longer matches its row"))
                .thenReturn(new Result(bid70, List.of()));

        assertThat(engine.place(3L, request, null, "AAPL")).isSameAs(bid70);

        verify(execution, times(2)).place(any(), any(), any(), any(), any(), any());
        verify(repository, times(2)).findBySymbolAndStatusOrderByCreatedAtAscIdAsc("AAPL", OrderStatus.OPEN);
    }

    @Test
    void eachSymbolMatchesOnItsOwnThread() {
        AtomicReference<String> thread = new AtomicReference<>();
        when(execution.place(any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
            thread.set(Thread.currentThread().getName());
            return new Result(bid90, List.of());
        });

        engine.place(3L, request, null, "AAPL");

        assertThat(thread.get()).isEqualTo("match-AAPL");
    }

    private void quoteAt(String price) {
        Quote quote = new Quote("AAPL", new BigDecimal(price), Instant.now());
        when(quotes.get("AAPL")).thenReturn(Optional.of(quote));
        when(quotes.isTradable(quote)).thenReturn(true);
    }

    private static Order limit(Long id, Long accountId, String price) {
        Order order = Order.limit(accountId, "AAPL", OrderSide.BUY, 1, new BigDecimal(price));
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }
}
