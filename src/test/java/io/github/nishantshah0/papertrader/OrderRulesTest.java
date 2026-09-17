package io.github.nishantshah0.papertrader;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nishantshah0.papertrader.account.AccountResponse;
import io.github.nishantshah0.papertrader.trading.OrderResponse;
import io.github.nishantshah0.papertrader.trading.OrderStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.core.type.TypeReference;

class OrderRulesTest extends ApiTestSupport {

    private static final TypeReference<List<OrderResponse>> ORDERS = new TypeReference<>() {};

    @Test
    void rejectedOrdersLeaveNothingBehind() throws Exception {
        AccountResponse account = createAccount();

        MvcTestResult tooExpensive = post(orders(account), "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":11}");
        assertThat(tooExpensive).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(detail(tooExpensive)).contains("insufficient cash");

        MvcTestResult nothingToSell = post(orders(account), "{\"symbol\":\"AAPL\",\"side\":\"SELL\",\"quantity\":1}");
        assertThat(nothingToSell).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(detail(nothingToSell)).contains("insufficient shares");

        assertThat(post(orders(account), "{\"symbol\":\"ZZZZ\",\"side\":\"BUY\",\"quantity\":1}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(post(orders(account),
                "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"quantity\":1,\"limitPrice\":90}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);

        assertThat(readList(get(orders(account)), ORDERS)).isEmpty();
        assertThat(portfolio(account).cash()).isEqualByComparingTo("1000.00");
    }

    @Test
    void malformedOrdersAre400() throws Exception {
        AccountResponse account = createAccount();
        assertThat(post(orders(account), "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":0}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(post(orders(account), "{\"symbol\":\"AAPL\",\"quantity\":1}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(post(orders(account), "{\"symbol\":\"AAPL\",\"side\":\"HOLD\",\"quantity\":1}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void filledOrdersCannotBeCancelled() throws Exception {
        AccountResponse account = createAccount();
        OrderResponse order = read(post(orders(account), "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}"),
                OrderResponse.class);

        MvcTestResult cancel = delete(orders(account) + "/" + order.id());
        assertThat(cancel).hasStatus(HttpStatus.CONFLICT);
        assertThat(detail(cancel)).contains("FILLED");
        assertThat(read(get(orders(account) + "/" + order.id()), OrderResponse.class).status())
                .isEqualTo(OrderStatus.FILLED);
        assertThat(delete(orders(account) + "/999999")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void ordersCanBeListedByStatus() throws Exception {
        AccountResponse account = createAccount();
        post(orders(account), "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}");
        post(orders(account), "{\"symbol\":\"MSFT\",\"side\":\"BUY\",\"quantity\":1}");

        assertThat(readList(get(orders(account)), ORDERS)).hasSize(2);
        assertThat(readList(get(orders(account) + "?status=FILLED"), ORDERS)).hasSize(2);
        assertThat(readList(get(orders(account) + "?status=OPEN"), ORDERS)).isEmpty();
    }
}
