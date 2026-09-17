package io.github.nishantshah0.papertrader;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nishantshah0.papertrader.account.AccountResponse;
import io.github.nishantshah0.papertrader.quote.Quote;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.core.type.TypeReference;

class AccountApiTest extends ApiTestSupport {

    @Test
    void newAccountsStartWithTheConfiguredCash() throws Exception {
        AccountResponse account = createAccount();
        assertThat(account.cash()).isEqualByComparingTo("1000.00");
        assertThat(account.startingCash()).isEqualByComparingTo("1000.00");

        AccountResponse fetched = read(get("/api/accounts/" + account.id()), AccountResponse.class);
        assertThat(fetched.id()).isEqualTo(account.id());
        assertThat(fetched.username()).isEqualTo(account.username());
        assertThat(fetched.cash()).isEqualByComparingTo("1000.00");
    }

    @Test
    void usernamesMustBeUniqueAndWellFormed() throws Exception {
        AccountResponse account = createAccount();
        MvcTestResult duplicate = post("/api/accounts", "{\"username\":\"" + account.username() + "\"}");
        assertThat(duplicate).hasStatus(HttpStatus.CONFLICT);
        assertThat(detail(duplicate)).contains("already taken");

        assertThat(post("/api/accounts", "{\"username\":\"AB\"}")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(post("/api/accounts", "{}")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownAccountsAre404() {
        assertThat(get("/api/accounts/999999")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(get("/api/accounts/999999/portfolio")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(get("/api/accounts/999999/orders")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(post("/api/accounts/999999/orders", "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void quotesComeFromTheCache() throws Exception {
        List<Quote> quotes = readList(get("/api/quotes"), new TypeReference<List<Quote>>() {});
        assertThat(quotes).extracting(Quote::symbol).contains("AAPL", "MSFT");

        Quote aapl = read(get("/api/quotes/aapl"), Quote.class);
        assertThat(aapl.symbol()).isEqualTo("AAPL");
        assertThat(aapl.price()).isEqualByComparingTo("100.00");

        assertThat(get("/api/quotes/ZZZZ")).hasStatus(HttpStatus.NOT_FOUND);
    }
}
