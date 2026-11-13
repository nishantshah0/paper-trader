package io.github.nishantshah0.papertrader.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class HouseQuoteTest {

    @Test
    void quotesHalfTheSpreadEitherSideOfTheFeedPrice() {
        var house = HouseQuote.around(new BigDecimal("100.00"), 100);

        assertThat(house.bid()).isEqualByComparingTo("99.50");
        assertThat(house.ask()).isEqualByComparingTo("100.50");
        assertThat(house.priceFor(OrderSide.BUY)).isEqualByComparingTo("100.50");
        assertThat(house.priceFor(OrderSide.SELL)).isEqualByComparingTo("99.50");
    }

    @Test
    void roundsOutwardSoTheSpreadNeverShrinks() {
        var house = HouseQuote.around(new BigDecimal("232.15"), 10);

        assertThat(house.bid()).isEqualByComparingTo("232.0339");
        assertThat(house.ask()).isEqualByComparingTo("232.2661");
    }

    @Test
    void aZeroSpreadTradesAtTheFeedPrice() {
        var house = HouseQuote.around(new BigDecimal("50"), 0);

        assertThat(house.bid()).isEqualByComparingTo("50");
        assertThat(house.ask()).isEqualByComparingTo("50");
    }

    @Test
    void refusesSpreadsOutsideTheSupportedRange() {
        assertThatThrownBy(() -> HouseQuote.around(BigDecimal.TEN, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HouseQuote.around(BigDecimal.TEN, 1_001))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
