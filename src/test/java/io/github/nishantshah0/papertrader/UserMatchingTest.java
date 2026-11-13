package io.github.nishantshah0.papertrader;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nishantshah0.papertrader.account.AccountResponse;
import io.github.nishantshah0.papertrader.quote.Quote;
import io.github.nishantshah0.papertrader.quote.QuoteCache;
import io.github.nishantshah0.papertrader.trading.MatchingEngine;
import io.github.nishantshah0.papertrader.trading.Order;
import io.github.nishantshah0.papertrader.trading.OrderResponse;
import io.github.nishantshah0.papertrader.trading.OrderService;
import io.github.nishantshah0.papertrader.trading.OrderSide;
import io.github.nishantshah0.papertrader.trading.OrderStatus;
import io.github.nishantshah0.papertrader.trading.OrderType;
import io.github.nishantshah0.papertrader.trading.PlaceOrderRequest;
import io.github.nishantshah0.papertrader.trading.TradeResponse;
import io.github.nishantshah0.papertrader.trading.TradeResponse.Counterparty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import tools.jackson.core.type.TypeReference;

// Every symbol is quoted at 50.00 with a 1% house spread, so the house bids 49.75 and
// asks 50.25. Orders posted inside that gap trade with other users first. Each test
// uses its own symbol so resting orders from one test never meet another.
@SpringBootTest(properties = {
        "papertrader.feed.mode=static",
        "papertrader.house.spread-bps=100",
        "papertrader.starting-cash=10000.00",
        "papertrader.seed-quotes.AAA=50.00",
        "papertrader.seed-quotes.BBB=50.00",
        "papertrader.seed-quotes.CCC=50.00",
        "papertrader.seed-quotes.DDD=50.00",
        "papertrader.seed-quotes.EEE=50.00",
        "papertrader.seed-quotes.FFF=50.00"
})
class UserMatchingTest extends ApiTestSupport {

    @Autowired
    OrderService service;

    @Autowired
    MatchingEngine engine;

    @Autowired
    QuoteCache quotes;

    @Test
    void usersInsideTheSpreadTradeWithEachOtherAndTheRestGoesToTheHouse() throws Exception {
        var seller = createAccount();
        var buyer = createAccount();
        service.place(seller.id(), market(OrderSide.BUY, "AAA", 10)); // 10 at the 50.25 ask
        Order ask = service.place(seller.id(), limit(OrderSide.SELL, "AAA", 10, "50.10"));
        assertThat(ask.getStatus()).isEqualTo(OrderStatus.OPEN);

        // the seller's 50.10 beats the house's 50.25
        var first = read(post(orders(buyer), "{\"symbol\":\"AAA\",\"side\":\"BUY\",\"quantity\":4}"),
                OrderResponse.class);
        assertThat(first.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(first.filledQuantity()).isEqualTo(4);
        assertThat(first.avgFillPrice()).isEqualByComparingTo("50.10");
        assertThat(portfolio(buyer).cash()).isEqualByComparingTo("9799.60");

        Order partlyFilled = service.get(seller.id(), ask.getId());
        assertThat(partlyFilled.getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(partlyFilled.getFilledQuantity()).isEqualTo(4);
        assertThat(portfolio(seller).cash()).isEqualByComparingTo("9697.90");
        assertThat(portfolio(seller).positions().get(0).quantity()).isEqualTo(6);
        assertThat(portfolio(seller).positions().get(0).availableQuantity()).isZero();

        // 6 left at 50.10 from the seller, then 2 from the house at 50.25
        Order second = service.place(buyer.id(), market(OrderSide.BUY, "AAA", 8));
        assertThat(second.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(second.getAvgFillPrice()).isEqualByComparingTo("50.1375");
        assertThat(portfolio(buyer).cash()).isEqualByComparingTo("9398.50");
        assertThat(service.get(seller.id(), ask.getId()).getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(portfolio(seller).cash()).isEqualByComparingTo("9998.50");

        List<TradeResponse> trades = trades(buyer);
        assertThat(trades).hasSize(3);
        assertThat(trades).filteredOn(trade -> trade.counterparty() == Counterparty.USER)
                .extracting(TradeResponse::quantity).containsExactlyInAnyOrder(4, 6);
        assertThat(trades).filteredOn(trade -> trade.counterparty() == Counterparty.HOUSE)
                .extracting(TradeResponse::quantity).containsExactly(2);
        assertThat(engine.restingOrders("AAA")).isZero();
    }

    @Test
    void aRestingOrderFillsInPartsAndCancellingKeepsWhatFilled() throws Exception {
        var buyer = createAccount();
        var seller = createAccount();
        Order bid = service.place(buyer.id(), limit(OrderSide.BUY, "BBB", 5, "49.90"));
        assertThat(portfolio(buyer).reservedCash()).isEqualByComparingTo("249.50");

        service.place(seller.id(), market(OrderSide.BUY, "BBB", 2));
        service.place(seller.id(), market(OrderSide.SELL, "BBB", 2)); // hits 49.90, not the house's 49.75

        Order partlyFilled = service.get(buyer.id(), bid.getId());
        assertThat(partlyFilled.getFilledQuantity()).isEqualTo(2);
        assertThat(partlyFilled.getAvgFillPrice()).isEqualByComparingTo("49.90");
        assertThat(portfolio(buyer).cash()).isEqualByComparingTo("9900.20");
        assertThat(portfolio(buyer).reservedCash()).isEqualByComparingTo("149.70"); // only the 3 still open
        assertThat(trades(seller)).filteredOn(trade -> trade.side() == OrderSide.SELL)
                .extracting(TradeResponse::counterparty).containsExactly(Counterparty.USER);

        assertThat(delete(orders(buyer) + "/" + bid.getId())).hasStatus(HttpStatus.OK);
        Order cancelled = service.get(buyer.id(), bid.getId());
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getFilledQuantity()).isEqualTo(2);
        assertThat(portfolio(buyer).reservedCash()).isEqualByComparingTo("0");
        assertThat(portfolio(buyer).positions().get(0).quantity()).isEqualTo(2);
        assertThat(engine.restingOrders("BBB")).isZero();
    }

    @Test
    void aPartlyFilledOrderFillsTheRestAgainstTheHouseWhenThePriceMoves() throws Exception {
        var buyer = createAccount();
        var seller = createAccount();
        Order bid = service.place(buyer.id(), limit(OrderSide.BUY, "CCC", 5, "49.90"));
        service.place(seller.id(), market(OrderSide.BUY, "CCC", 2));
        service.place(seller.id(), market(OrderSide.SELL, "CCC", 2));
        try {
            // the house now asks 49.7475, inside the bid's limit
            quotes.update(new Quote("CCC", new BigDecimal("49.50"), Instant.now()));
            assertThat(service.match(buyer.id(), bid.getId())).isEqualTo(OrderStatus.FILLED);

            Order filled = service.get(buyer.id(), bid.getId());
            assertThat(filled.getFilledQuantity()).isEqualTo(5);
            assertThat(filled.getAvgFillPrice()).isEqualByComparingTo("49.8085");
            assertThat(portfolio(buyer).cash()).isEqualByComparingTo("9750.96");
            assertThat(trades(buyer)).hasSize(2);
        } finally {
            quotes.update(new Quote("CCC", new BigDecimal("50.00"), Instant.now()));
        }
    }

    @Test
    void anOrderThatWouldTradeWithItsOwnRestingOrderIsRefused() throws Exception {
        var account = createAccount();
        service.place(account.id(), market(OrderSide.BUY, "DDD", 5));
        service.place(account.id(), limit(OrderSide.SELL, "DDD", 5, "50.10"));
        var cashBefore = portfolio(account).cash();

        var selfTrade = post(orders(account), "{\"symbol\":\"DDD\",\"side\":\"BUY\",\"quantity\":1}");

        assertThat(selfTrade).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(detail(selfTrade)).contains("your own resting order");
        assertThat(service.list(account.id(), null)).hasSize(2);
        assertThat(portfolio(account).cash()).isEqualByComparingTo(cashBefore);
    }

    // Two matcher threads each trade between the same two accounts, one on EEE and one
    // on FFF. Both transactions lock the pair of accounts; taking the locks in ID order
    // is what stops each from holding the lock the other is waiting for.
    @Test
    void tradesBetweenTheSameAccountsOnTwoSymbolsAtOnceDoNotDeadlock() throws Exception {
        var a = createAccount();
        var b = createAccount();
        service.place(a.id(), market(OrderSide.BUY, "EEE", 10));
        service.place(b.id(), market(OrderSide.BUY, "FFF", 10));

        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int round = 0; round < 10; round++) {
                service.place(a.id(), limit(OrderSide.SELL, "EEE", 1, "50.10"));
                service.place(b.id(), limit(OrderSide.SELL, "FFF", 1, "50.10"));
                var start = new CountDownLatch(1);
                var aBuys = pool.submit(() -> {
                    start.await();
                    return service.place(a.id(), market(OrderSide.BUY, "FFF", 1));
                });
                var bBuys = pool.submit(() -> {
                    start.await();
                    return service.place(b.id(), market(OrderSide.BUY, "EEE", 1));
                });
                start.countDown();
                assertThat(aBuys.get(10, TimeUnit.SECONDS).getAvgFillPrice()).isEqualByComparingTo("50.10");
                assertThat(bBuys.get(10, TimeUnit.SECONDS).getAvgFillPrice()).isEqualByComparingTo("50.10");
            }
        }

        assertThat(service.list(a.id(), OrderStatus.OPEN)).isEmpty();
        assertThat(service.list(b.id(), OrderStatus.OPEN)).isEmpty();
        assertThat(portfolio(a).cash()).isEqualByComparingTo("9497.50");
        assertThat(portfolio(b).cash()).isEqualByComparingTo("9497.50");
        assertThat(trades(a)).hasSize(21);
    }

    private List<TradeResponse> trades(AccountResponse account) throws Exception {
        return readList(get("/api/accounts/" + account.id() + "/trades"), new TypeReference<List<TradeResponse>>() {});
    }

    private static PlaceOrderRequest market(OrderSide side, String symbol, int quantity) {
        return new PlaceOrderRequest(symbol, side, null, quantity, null);
    }

    private static PlaceOrderRequest limit(OrderSide side, String symbol, int quantity, String price) {
        return new PlaceOrderRequest(symbol, side, OrderType.LIMIT, quantity, new BigDecimal(price));
    }
}
