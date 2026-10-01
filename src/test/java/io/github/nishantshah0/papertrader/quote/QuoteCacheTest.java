package io.github.nishantshah0.papertrader.quote;
import static org.assertj.core.api.Assertions.*;
import io.github.nishantshah0.papertrader.PaperTraderProperties;
import io.github.nishantshah0.papertrader.trading.OrderRejectedException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuoteCacheTest {
    private QuoteCache cache(String mode) { return new QuoteCache(new PaperTraderProperties(new BigDecimal("1000"), Map.of("AAPL",new BigDecimal("100"))),mode); }
    @Test void historyIsBoundedOrderedAndCannotBeMutated() {
        var cache = cache("finnhub");
        assertThat(cache.history().get("AAPL")).isEmpty();
        var start = Instant.now().minusSeconds(400);
        for (int i = 0; i < 300; i++) cache.update(new Quote("AAPL", new BigDecimal("100"), start.plusSeconds(i)));
        var samples = cache.history().get("AAPL");
        assertThat(samples).hasSize(240);
        assertThat(samples.getFirst().asOf()).isEqualTo(start.plusSeconds(60));
        cache.update(new Quote("AAPL", new BigDecimal("99"), start));
        assertThat(cache.history().get("AAPL")).isEqualTo(samples);
        cache.update(new Quote("AAPL", new BigDecimal("101"), start.plusSeconds(299)));
        assertThat(cache.history().get("AAPL")).hasSize(240);
        assertThat(cache.history().get("AAPL").getLast().price()).isEqualByComparingTo("101");
        assertThatThrownBy(() -> samples.clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void liveModeNeverTradesAtSeedOrStalePrices() {
        var cache=cache("finnhub");
        assertThatThrownBy(()->cache.requireTradable(cache.require("AAPL"))).isInstanceOf(OrderRejectedException.class);
        cache.update(new Quote("AAPL",new BigDecimal("110"),Instant.now().minusSeconds(180)));
        assertThat(cache.isTradable(cache.require("AAPL"))).isFalse();
        cache.update(new Quote("AAPL",new BigDecimal("115"),Instant.now()));
        assertThat(cache.isTradable(cache.require("AAPL"))).isTrue();
    }
    @Test void olderAndInvalidTicksCannotReplaceGoodData() {
        var cache=cache("demo"); var now=Instant.now();
        cache.update(new Quote("AAPL",new BigDecimal("115"),now));
        cache.update(new Quote("AAPL",new BigDecimal("90"),now.minusSeconds(1)));
        assertThat(cache.require("AAPL").price()).isEqualByComparingTo("115");
        assertThatThrownBy(()->cache.update(new Quote("AAPL",BigDecimal.ZERO,now))).isInstanceOf(IllegalArgumentException.class);
    }
}
