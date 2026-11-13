package io.github.nishantshah0.papertrader.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.github.nishantshah0.papertrader.trading.OrderBook.PlannedFill;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderBookTest {

    private static final long TAKER = 99;
    private static final HouseQuote HOUSE = new HouseQuote(new BigDecimal("99.50"), new BigDecimal("100.50"));

    private final OrderBook book = new OrderBook("AAPL");

    @Test
    void aTakerWalksTheBookBestPriceFirstThenOldestFirst() {
        book.add(sell(1, 1, "100.30", 5));
        book.add(sell(2, 2, "100.10", 3));
        book.add(sell(3, 3, "100.10", 4));

        var fills = book.match(OrderSide.BUY, null, 10, TAKER, HOUSE);

        assertThat(fills).extracting(fill -> fill.maker().id(), PlannedFill::quantity, PlannedFill::price)
                .containsExactly(
                        tuple(2L, 3, new BigDecimal("100.10")),
                        tuple(3L, 4, new BigDecimal("100.10")),
                        tuple(1L, 3, new BigDecimal("100.30")));
    }

    @Test
    void matchingOnlyPlans() {
        book.add(sell(1, 1, "100.10", 5));

        book.match(OrderSide.BUY, null, 5, TAKER, HOUSE);

        assertThat(book.get(1)).hasValueSatisfying(order -> assertThat(order.remaining()).isEqualTo(5));
    }

    @Test
    void theTakerStopsAtItsLimit() {
        book.add(sell(1, 1, "100.10", 5));
        book.add(sell(2, 2, "100.20", 5));

        var fills = book.match(OrderSide.BUY, new BigDecimal("100.15"), 10, TAKER, HOUSE);

        assertThat(fills).extracting(fill -> fill.maker().id()).containsExactly(1L);
    }

    @Test
    void usersPricedWorseThanTheHouseAreSkippedButAnEqualPriceGoesToTheUser() {
        book.add(sell(1, 1, "100.50", 2));
        book.add(sell(2, 2, "100.60", 2));
        book.add(buy(3, 3, "99.40", 2));

        assertThat(book.match(OrderSide.BUY, null, 10, TAKER, HOUSE))
                .extracting(fill -> fill.maker().id()).containsExactly(1L);
        assertThat(book.match(OrderSide.SELL, null, 10, TAKER, HOUSE)).isEmpty();
    }

    @Test
    void withoutAHouseQuoteUsersTradeAtAnyPriceWithinTheLimit() {
        book.add(sell(1, 1, "120", 2));

        assertThat(book.match(OrderSide.BUY, new BigDecimal("125"), 2, TAKER, null)).hasSize(1);
    }

    @Test
    void aTakerCannotTradeWithItsOwnRestingOrder() {
        book.add(sell(1, TAKER, "100.10", 2));

        assertThatThrownBy(() -> book.match(OrderSide.BUY, null, 1, TAKER, HOUSE))
                .isInstanceOf(OrderRejectedException.class)
                .hasMessageContaining("your own resting order #1");
        // an own order the taker never reaches is fine
        assertThat(book.match(OrderSide.BUY, new BigDecimal("100"), 1, TAKER, HOUSE)).isEmpty();
    }

    @Test
    void aPartialFillKeepsTheOrdersPlaceInTheQueue() {
        book.add(sell(1, 1, "100.10", 5));
        book.add(sell(2, 2, "100.10", 5));

        book.reduce(1, 3);

        assertThat(book.match(OrderSide.BUY, null, 4, TAKER, HOUSE))
                .extracting(fill -> fill.maker().id(), PlannedFill::quantity)
                .containsExactly(tuple(1L, 2), tuple(2L, 2));
        assertThat(book.depth(OrderSide.SELL, 5)).containsExactly(new OrderBook.Level(new BigDecimal("100.10"), 7, 2));
    }

    @Test
    void reducingAnOrderToZeroRemovesItAndItsLevel() {
        book.add(buy(1, 1, "99.80", 5));

        book.reduce(1, 5);

        assertThat(book.contains(1)).isFalse();
        assertThat(book.bestBid()).isEmpty();
        assertThatThrownBy(() -> book.reduce(1, 1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theHouseCrossesBidsAtOrAboveItsAskAndAsksAtOrBelowItsBid() {
        book.add(buy(1, 1, "100.50", 1));
        book.add(buy(2, 2, "100.40", 1));
        book.add(sell(3, 3, "99.50", 1));
        book.add(sell(4, 4, "99.60", 1));

        assertThat(book.crossedBy(HOUSE)).extracting(RestingOrder::id).containsExactly(1L, 3L);
    }

    @Test
    void depthAggregatesEachLevelBestFirst() {
        book.add(buy(1, 1, "99.80", 5));
        book.add(buy(2, 2, "99.80", 1));
        book.add(buy(3, 3, "99.90", 2));

        assertThat(book.depth(OrderSide.BUY, 5)).containsExactly(
                new OrderBook.Level(new BigDecimal("99.90"), 2, 1),
                new OrderBook.Level(new BigDecimal("99.80"), 6, 2));
        assertThat(book.depth(OrderSide.BUY, 1)).hasSize(1);
    }

    @Test
    void cancelRemovesOnlyThatOrderAndTidiesTheLevel() {
        book.add(buy(1, 1, "100", 1));
        book.add(buy(2, 2, "100", 1));

        assertThat(book.remove(1)).isPresent();
        assertThat(book.remove(1)).isEmpty();
        assertThat(book.contains(2)).isTrue();
        book.remove(2);
        assertThat(book.bestBid()).isEmpty();
    }

    @Test
    void addingTheSameOrderTwiceKeepsOneCopy() {
        book.add(buy(1, 1, "100", 1));
        book.add(buy(1, 1, "100", 1));

        assertThat(book.size()).isEqualTo(1);
    }

    @Test
    void rejectsOrdersForAnotherSymbol() {
        RestingOrder msft = new RestingOrder(9, 1, "MSFT", OrderSide.BUY, new BigDecimal("100"), 1);
        assertThatThrownBy(() -> book.add(msft)).isInstanceOf(IllegalArgumentException.class);
    }

    private static RestingOrder buy(long id, long accountId, String limit, int shares) {
        return new RestingOrder(id, accountId, "AAPL", OrderSide.BUY, new BigDecimal(limit), shares);
    }

    private static RestingOrder sell(long id, long accountId, String limit, int shares) {
        return new RestingOrder(id, accountId, "AAPL", OrderSide.SELL, new BigDecimal(limit), shares);
    }
}
