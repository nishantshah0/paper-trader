package io.github.nishantshah0.papertrader.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderBookTest {

    private final OrderBook book = new OrderBook("AAPL");

    @Test
    void bidsDrainBestPriceFirstThenInArrivalOrder() {
        book.add(buy(1, "100"));
        book.add(buy(2, "101"));
        book.add(buy(3, "100"));

        assertThat(book.bestBid()).contains(new BigDecimal("101"));
        assertThat(book.crossing(new BigDecimal("100.50"))).extracting(RestingOrder::id).containsExactly(2L);
        assertThat(book.crossing(new BigDecimal("100"))).extracting(RestingOrder::id).containsExactly(1L, 3L);
        assertThat(book.size()).isZero();
        assertThat(book.bestBid()).isEmpty();
    }

    @Test
    void asksDrainLowestPriceFirst() {
        book.add(sell(4, "50"));
        book.add(sell(5, "49"));

        assertThat(book.bestAsk()).contains(new BigDecimal("49"));
        assertThat(book.crossing(new BigDecimal("49.50"))).extracting(RestingOrder::id).containsExactly(5L);
        assertThat(book.crossing(new BigDecimal("50"))).extracting(RestingOrder::id).containsExactly(4L);
    }

    @Test
    void aPriceBetweenTheSidesCrossesNothing() {
        book.add(buy(1, "90"));
        book.add(sell(2, "110"));

        assertThat(book.crossing(new BigDecimal("100"))).isEmpty();
        assertThat(book.size()).isEqualTo(2);
    }

    @Test
    void onePriceCanCrossBothSides() {
        book.add(buy(1, "100"));
        book.add(sell(2, "90"));

        assertThat(book.crossing(new BigDecimal("95"))).extracting(RestingOrder::id).containsExactly(1L, 2L);
    }

    @Test
    void cancelRemovesOnlyThatOrderAndTidiesTheLevel() {
        book.add(buy(1, "100"));
        book.add(buy(2, "100"));

        assertThat(book.remove(1)).isPresent();
        assertThat(book.remove(1)).isEmpty();
        assertThat(book.contains(2)).isTrue();
        assertThat(book.crossing(new BigDecimal("100"))).extracting(RestingOrder::id).containsExactly(2L);
        assertThat(book.bestBid()).isEmpty();
    }

    @Test
    void addingTheSameOrderTwiceKeepsOneCopy() {
        book.add(buy(1, "100"));
        book.add(buy(1, "100"));

        assertThat(book.size()).isEqualTo(1);
        assertThat(book.crossing(new BigDecimal("100"))).hasSize(1);
    }

    @Test
    void rejectsOrdersForAnotherSymbol() {
        RestingOrder msft = new RestingOrder(9, 1, "MSFT", OrderSide.BUY, new BigDecimal("100"), 1);
        assertThatThrownBy(() -> book.add(msft)).isInstanceOf(IllegalArgumentException.class);
    }

    private static RestingOrder buy(long id, String limit) {
        return new RestingOrder(id, 1, "AAPL", OrderSide.BUY, new BigDecimal(limit), 1);
    }

    private static RestingOrder sell(long id, String limit) {
        return new RestingOrder(id, 1, "AAPL", OrderSide.SELL, new BigDecimal(limit), 1);
    }
}
