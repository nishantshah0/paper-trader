package io.github.nishantshah0.papertrader;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nishantshah0.papertrader.account.AccountResponse;
import io.github.nishantshah0.papertrader.portfolio.PortfolioResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// a small balance and round prices keep the arithmetic in the assertions obvious
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "papertrader.starting-cash=1000.00",
        "papertrader.seed-quotes.AAPL=100.00",
        "papertrader.seed-quotes.MSFT=50.00"
})
@AutoConfigureMockMvc
abstract class ApiTestSupport {

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected ObjectMapper json;

    protected AccountResponse createAccount() throws Exception {
        String username = "t_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        MvcTestResult result = post("/api/accounts", "{\"username\":\"" + username + "\"}");
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return read(result, AccountResponse.class);
    }

    protected String orders(AccountResponse account) {
        return "/api/accounts/" + account.id() + "/orders";
    }

    protected PortfolioResponse portfolio(AccountResponse account) throws Exception {
        return read(get("/api/accounts/" + account.id() + "/portfolio"), PortfolioResponse.class);
    }

    protected MvcTestResult post(String uri, String body) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    protected MvcTestResult get(String uri) {
        return mvc.get().uri(uri).exchange();
    }

    protected MvcTestResult delete(String uri) {
        return mvc.delete().uri(uri).exchange();
    }

    protected <T> T read(MvcTestResult result, Class<T> type) throws Exception {
        return json.readValue(result.getResponse().getContentAsString(), type);
    }

    protected <T> List<T> readList(MvcTestResult result, TypeReference<List<T>> type) throws Exception {
        return json.readValue(result.getResponse().getContentAsString(), type);
    }

    protected String detail(MvcTestResult result) throws Exception {
        return (String) json.readValue(result.getResponse().getContentAsString(), Map.class).get("detail");
    }
}
