package io.github.nishantshah0.papertrader;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nishantshah0.papertrader.account.AccountResponse;
import io.github.nishantshah0.papertrader.portfolio.PortfolioResponse;
import io.github.nishantshah0.papertrader.quote.Quote;
import io.github.nishantshah0.papertrader.quote.QuoteCache;
import io.github.nishantshah0.papertrader.trading.OrderResponse;
import io.github.nishantshah0.papertrader.trading.OrderStatus;
import io.github.nishantshah0.papertrader.trading.OrderType;
import io.github.nishantshah0.papertrader.trading.TradeResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.core.type.TypeReference;

class MarketOrderTest extends ApiTestSupport {

    @Autowired
    private QuoteCache quotes;

    @Test
    void marketBuyMovesCashIntoAPosition() throws Exception {
        AccountResponse account = createAccount();

        MvcTestResult placed = post(orders(account), "{\"symbol\":\"aapl\",\"side\":\"BUY\",\"quantity\":3}");
        assertThat(placed).hasStatus(HttpStatus.CREATED);
        OrderResponse order = read(placed, OrderResponse.class);
        assertThat(order.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(order.type()).isEqualTo(OrderType.MARKET);
        assertThat(order.symbol()).isEqualTo("AAPL");

        PortfolioResponse portfolio = portfolio(account);
        assertThat(portfolio.cash()).isEqualByComparingTo("700.00");
        assertThat(portfolio.positions()).hasSize(1);
        PortfolioResponse.PositionView aapl = portfolio.positions().get(0);
        assertThat(aapl.symbol()).isEqualTo("AAPL");
        assertThat(aapl.quantity()).isEqualTo(3);
        assertThat(aapl.avgCost()).isEqualByComparingTo("100.00");
        assertThat(aapl.marketValue()).isEqualByComparingTo("300.00");
        assertThat(aapl.unrealizedPnl()).isEqualByComparingTo("0.00");
        assertThat(portfolio.totalValue()).isEqualByComparingTo("1000.00");

        List<TradeResponse> trades = readList(get("/api/accounts/" + account.id() + "/trades"),
                new TypeReference<List<TradeResponse>>() {});
        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).orderId()).isEqualTo(order.id());
        assertThat(trades.get(0).price()).isEqualByComparingTo("100.00");
        assertThat(trades.get(0).notional()).isEqualByComparingTo("300.00");
    }

    @Test
    void sellingAfterAPriceMoveRealisesPnl() throws Exception {
        AccountResponse account = createAccount();
        quotes.update(new Quote("MSFT", new BigDecimal("50.00"), Instant.now()));
        assertThat(post(orders(account), "{\"symbol\":\"MSFT\",\"side\":\"BUY\",\"quantity\":4}"))
                .hasStatus(HttpStatus.CREATED);

        quotes.update(new Quote("MSFT", new BigDecimal("60.00"), Instant.now()));
        assertThat(post(orders(account), "{\"symbol\":\"MSFT\",\"side\":\"SELL\",\"quantity\":1}"))
                .hasStatus(HttpStatus.CREATED);

        PortfolioResponse portfolio = portfolio(account);
        assertThat(portfolio.cash()).isEqualByComparingTo("860.00");
        PortfolioResponse.PositionView msft = portfolio.positions().get(0);
        assertThat(msft.quantity()).isEqualTo(3);
        assertThat(msft.avgCost()).isEqualByComparingTo("50.00");
        assertThat(msft.lastPrice()).isEqualByComparingTo("60.00");
        assertThat(msft.realizedPnl()).isEqualByComparingTo("10.00");
        assertThat(msft.unrealizedPnl()).isEqualByComparingTo("30.00");
        assertThat(portfolio.totalValue()).isEqualByComparingTo("1040.00");
        assertThat(portfolio.totalPnl()).isEqualByComparingTo("40.00");
    }
}
