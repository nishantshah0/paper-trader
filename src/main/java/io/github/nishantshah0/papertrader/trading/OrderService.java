package io.github.nishantshah0.papertrader.trading;

import io.github.nishantshah0.papertrader.NotFoundException;
import io.github.nishantshah0.papertrader.account.AccountRepository;
import io.github.nishantshah0.papertrader.quote.QuoteCache;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The entry point for orders. Anything that can fill or change the book goes through
// the MatchingEngine, which runs it on the symbol's matcher thread; reads go straight
// to the database.
//
// The routed methods are deliberately not transactional: the request thread only waits
// for the matcher, and holding a database connection while it waits could starve the
// matcher threads of connections.
@Service
public class OrderService {

    private final AccountRepository accounts;
    private final OrderRepository orders;
    private final TradeRepository trades;
    private final QuoteCache quotes;
    private final MatchingEngine engine;

    OrderService(AccountRepository accounts, OrderRepository orders, TradeRepository trades, QuoteCache quotes,
            MatchingEngine engine) {
        this.accounts = accounts;
        this.orders = orders;
        this.trades = trades;
        this.quotes = quotes;
        this.engine = engine;
    }

    public Order place(Long accountId, PlaceOrderRequest request) {
        return place(accountId, request, null);
    }

    public Order place(Long accountId, PlaceOrderRequest request, String key) {
        requireAccount(accountId);
        String symbol = request.symbol().toUpperCase(Locale.ROOT);
        if (quotes.get(symbol).isEmpty()) {
            throw new OrderRejectedException("unknown symbol " + symbol); // before it gets a matcher thread
        }
        return engine.place(accountId, request, key, symbol);
    }

    public Order cancel(Long accountId, Long orderId) {
        return engine.cancel(accountId, orderId, find(accountId, orderId).getSymbol());
    }

    // fills a resting order against the current house quote if it crosses; the quote
    // feed does this for every order on each tick, tests call it directly
    public OrderStatus match(Long accountId, Long orderId) {
        return engine.match(orderId, find(accountId, orderId).getSymbol());
    }

    @Transactional(readOnly = true)
    public Order get(Long accountId, Long orderId) {
        return find(accountId, orderId);
    }

    @Transactional(readOnly = true)
    public List<Order> list(Long accountId, OrderStatus status) {
        requireAccount(accountId);
        return status == null
                ? orders.findByAccountIdOrderByCreatedAtDesc(accountId)
                : orders.findByAccountIdAndStatusOrderByCreatedAtDesc(accountId, status);
    }

    @Transactional(readOnly = true)
    public List<Trade> trades(Long accountId) {
        requireAccount(accountId);
        return trades.findByAccountIdOrderByExecutedAtDesc(accountId);
    }

    private Order find(Long accountId, Long orderId) {
        return orders.findByIdAndAccountId(orderId, accountId)
                .orElseThrow(() -> new NotFoundException("order " + orderId + " not found"));
    }

    private void requireAccount(Long accountId) {
        if (!accounts.existsById(accountId)) {
            throw new NotFoundException("account " + accountId + " not found");
        }
    }
}
