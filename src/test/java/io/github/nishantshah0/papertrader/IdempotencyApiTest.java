package io.github.nishantshah0.papertrader;
import static org.assertj.core.api.Assertions.assertThat;
import io.github.nishantshah0.papertrader.trading.OrderResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

class IdempotencyApiTest extends ApiTestSupport {
    @Test void retryHeaderReturnsOriginalOrderAndConflictingPayloadReturns409() throws Exception {
        var a=createAccount(); String body="{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}";
        var first=mvc.post().uri(orders(a)).contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key","retry").content(body).exchange();
        var second=mvc.post().uri(orders(a)).contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key","retry").content(body).exchange();
        assertThat(first).hasStatus(HttpStatus.CREATED);assertThat(second).hasStatus(HttpStatus.CREATED);
        assertThat(read(first,OrderResponse.class).id()).isEqualTo(read(second,OrderResponse.class).id());
        assertThat(portfolio(a).cash()).isEqualByComparingTo("900");
        assertThat(mvc.post().uri(orders(a)).contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key","retry").content(body.replace(":1}",":2}")).exchange()).hasStatus(HttpStatus.CONFLICT);
    }
}
