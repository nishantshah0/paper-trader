package io.github.nishantshah0.papertrader.trading;

import io.github.nishantshah0.papertrader.quote.Quote;
import io.github.nishantshah0.papertrader.quote.QuoteCache;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Resting limit orders live in one in-memory OrderBook per symbol. A quote pops only
// the orders it crosses instead of scanning every open order.
//
// The book is an index, not the record. Postgres holds order status, and
// OrderService.match re-reads the order under the account row lock before filling.
// That is what makes the races safe:
//  - a cancel that commits after its order was popped: match sees CANCELLED, no fill
//  - a stale entry left behind by a failed retry: same check, it is dropped
//  - a restart: the books are rebuilt from OPEN rows on startup
// Each book is guarded by its own monitor, so a quote for AAPL never waits on MSFT.
@Component
@ConditionalOnProperty(name = "papertrader.engine.enabled", havingValue = "true", matchIfMissing = true)
public class MatchingEngine {

    private static final Logger log = LoggerFactory.getLogger(MatchingEngine.class);

    private final Map<String, OrderBook> books = new ConcurrentHashMap<>();
    private final OrderRepository orders;
    private final OrderService service;
    private final QuoteCache quotes;

    MatchingEngine(OrderRepository orders, OrderService service, QuoteCache quotes) {
        this.orders = orders;
        this.service = service;
        this.quotes = quotes;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rebuild() {
        int rested = 0;
        for (Order order : orders.findByStatusOrderByCreatedAtAscIdAsc(OrderStatus.OPEN)) {
            if (order.getType() == OrderType.LIMIT) {
                rest(RestingOrder.of(order));
                rested++;
            }
        }
        log.info("Order books rebuilt with {} resting limit orders", rested);
        quotes.symbols().forEach(symbol -> quotes.get(symbol).ifPresent(this::onQuote));
    }

    // Runs on the feed thread for every new quote.
    @EventListener
    public void onQuote(Quote quote) {
        OrderBook book = books.get(quote.symbol());
        if (book == null) {
            return;
        }
        List<RestingOrder> crossed;
        synchronized (book) {
            crossed = book.crossing(quote.price());
        }
        // fill outside the book lock: a fill is a database transaction, and holding the
        // lock through it would block cancels and placements for this symbol
        for (RestingOrder order : crossed) {
            try {
                if (service.match(order.accountId(), order.id()) == OrderStatus.OPEN) {
                    rest(order); // quote not tradable yet, e.g. stale; wait for the next one
                }
            } catch (RuntimeException e) {
                log.warn("Order {} could not be matched, keeping it in the book ({})", order.id(),
                        e.getClass().getSimpleName());
                rest(order);
            }
        }
    }

    // Only committed state reaches the book. A new resting order is checked against the
    // current quote straight away, so a quote that landed between commit and insertion
    // is not missed.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderChanged(OrderChanged event) {
        if (event.status() != OrderStatus.OPEN) {
            books.values().forEach(book -> {
                synchronized (book) {
                    book.remove(event.orderId());
                }
            });
            return;
        }
        orders.findById(event.orderId())
                .filter(order -> order.getType() == OrderType.LIMIT && order.isOpen())
                .ifPresent(order -> {
                    rest(RestingOrder.of(order));
                    quotes.get(order.getSymbol()).ifPresent(this::onQuote);
                });
    }

    private void rest(RestingOrder order) {
        OrderBook book = books.computeIfAbsent(order.symbol(), OrderBook::new);
        synchronized (book) {
            book.add(order);
        }
    }

    public int restingOrders(String symbol) {
        OrderBook book = books.get(symbol);
        if (book == null) {
            return 0;
        }
        synchronized (book) {
            return book.size();
        }
    }
}
