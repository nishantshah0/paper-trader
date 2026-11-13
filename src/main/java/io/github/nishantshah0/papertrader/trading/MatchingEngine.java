package io.github.nishantshah0.papertrader.trading;

import io.github.nishantshah0.papertrader.quote.Quote;
import io.github.nishantshah0.papertrader.quote.QuoteCache;
import io.github.nishantshah0.papertrader.trading.OrderBook.Level;
import io.github.nishantshah0.papertrader.trading.OrderExecution.BookChange;
import io.github.nishantshah0.papertrader.trading.OrderExecution.BookOutOfSyncException;
import io.github.nishantshah0.papertrader.trading.OrderExecution.Result;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// One matcher per symbol: a single thread that owns the symbol's OrderBook and runs
// every placement, cancel and quote-driven fill for it, one at a time. Within a symbol
// there are no races left to reason about, and symbols match in parallel.
//
// The book is an index, not the record. Postgres holds every order, and each change is
// its own transaction that locks the accounts it touches. The book is updated only after
// that transaction commits, from the changes it reports, so a rollback leaves the book as
// it was. If the book and the database ever disagree, the symbol's book is reloaded from
// its OPEN rows, which is also how it is built on startup.
@Component
public class MatchingEngine {

    private static final Logger log = LoggerFactory.getLogger(MatchingEngine.class);
    private static final long TIMEOUT_SECONDS = 10;

    private final Map<String, Matcher> matchers = new ConcurrentHashMap<>();
    private final OrderRepository orders;
    private final OrderExecution execution;
    private final QuoteCache quotes;
    private final int spreadBps;

    MatchingEngine(OrderRepository orders, OrderExecution execution, QuoteCache quotes,
            @Value("${papertrader.house.spread-bps:10}") int spreadBps) {
        this.orders = orders;
        this.execution = execution;
        this.quotes = quotes;
        this.spreadBps = spreadBps;
        HouseQuote.around(BigDecimal.ONE, spreadBps); // fail at startup on a bad spread
    }

    public record BookView(String symbol, HouseQuote house, List<Level> bids, List<Level> asks) {
    }

    private static final class Matcher {
        final OrderBook book;
        final ExecutorService thread;

        Matcher(String symbol) {
            this.book = new OrderBook(symbol);
            this.thread = Executors.newSingleThreadExecutor(
                    Thread.ofPlatform().name("match-" + symbol).daemon().factory());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rebuild() {
        var symbols = new TreeSet<>(quotes.symbols());
        orders.findByStatusOrderByCreatedAtAscIdAsc(OrderStatus.OPEN).forEach(order -> symbols.add(order.getSymbol()));
        for (String symbol : symbols) {
            submit(symbol, () -> {
                reload(symbol);
                sweep(symbol);
                return null;
            });
        }
    }

    public Order place(Long accountId, PlaceOrderRequest request, String key, String symbol) {
        return call(symbol, () -> {
            Matcher matcher = matcher(symbol);
            try {
                return apply(matcher, execution.place(accountId, request, key, symbol, matcher.book, house(symbol)));
            } catch (BookOutOfSyncException e) {
                log.warn("{} book out of sync, reloading before retrying ({})", symbol, e.getMessage());
                reload(symbol);
                return apply(matcher, execution.place(accountId, request, key, symbol, matcher.book, house(symbol)));
            }
        });
    }

    public Order cancel(Long accountId, Long orderId, String symbol) {
        return call(symbol, () -> apply(matcher(symbol), execution.cancel(accountId, orderId)));
    }

    // fills one resting order against the current house quote, if it crosses
    public OrderStatus match(long orderId, String symbol) {
        return call(symbol, () -> {
            Matcher matcher = matcher(symbol);
            RestingOrder resting = matcher.book.get(orderId).orElse(null);
            if (resting == null) {
                return orders.findById(orderId).map(Order::getStatus).orElseThrow();
            }
            return apply(matcher, execution.fillAgainstHouse(resting, house(symbol))).getStatus();
        });
    }

    // Runs on the feed thread for every new quote. It only queues the sweep, so a slow
    // fill for one symbol never holds up quotes for the others.
    @EventListener
    public void onQuote(Quote quote) {
        submit(quote.symbol(), () -> {
            sweep(quote.symbol());
            return null;
        });
    }

    public BookView book(String symbol, int levels) {
        return call(symbol, () -> {
            OrderBook book = matcher(symbol).book;
            return new BookView(symbol, house(symbol), book.depth(OrderSide.BUY, levels),
                    book.depth(OrderSide.SELL, levels));
        });
    }

    public int restingOrders(String symbol) {
        return call(symbol, () -> matcher(symbol).book.size());
    }

    // every resting order the house quote now crosses fills in full, each in its own
    // transaction, so one account's failure does not stop the rest
    private void sweep(String symbol) {
        HouseQuote house = house(symbol);
        if (house == null) {
            return;
        }
        Matcher matcher = matcher(symbol);
        for (RestingOrder resting : matcher.book.crossedBy(house)) {
            try {
                apply(matcher, execution.fillAgainstHouse(resting, house));
            } catch (RuntimeException e) {
                log.warn("Order {} could not be filled, keeping it in the book ({})", resting.id(),
                        e.getClass().getSimpleName());
            }
        }
    }

    private void reload(String symbol) {
        Matcher matcher = matcher(symbol);
        matcher.book.clear();
        int rested = 0;
        for (Order order : orders.findBySymbolAndStatusOrderByCreatedAtAscIdAsc(symbol, OrderStatus.OPEN)) {
            if (order.getType() == OrderType.LIMIT) {
                matcher.book.add(RestingOrder.of(order));
                rested++;
            }
        }
        log.info("{} book loaded with {} resting limit orders", symbol, rested);
    }

    private Order apply(Matcher matcher, Result result) {
        for (BookChange change : result.changes()) {
            switch (change) {
                case BookChange.Reduce reduce -> matcher.book.reduce(reduce.orderId(), reduce.shares());
                case BookChange.Remove remove -> matcher.book.remove(remove.orderId());
                case BookChange.Rest rest -> matcher.book.add(rest.order());
            }
        }
        return result.order();
    }

    private HouseQuote house(String symbol) {
        Quote quote = quotes.get(symbol).orElse(null);
        return quote == null || !quotes.isTradable(quote) ? null : HouseQuote.around(quote.price(), spreadBps);
    }

    private Matcher matcher(String symbol) {
        return matchers.computeIfAbsent(symbol, Matcher::new);
    }

    private <T> Future<T> submit(String symbol, Callable<T> task) {
        return matcher(symbol).thread.submit(task);
    }

    // waits for a task on the symbol's thread and rethrows what it threw, so callers see
    // the same OrderRejectedException or NotFoundException as a direct call would raise
    private <T> T call(String symbol, Callable<T> task) {
        var future = submit(symbol, task);
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(e.getCause());
        } catch (TimeoutException e) {
            future.cancel(false);
            throw new EngineBusyException(symbol);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EngineBusyException(symbol);
        }
    }

    @PreDestroy
    void stop() {
        matchers.values().forEach(matcher -> matcher.thread.shutdownNow());
    }
}
