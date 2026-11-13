# Architecture and execution guarantees

## Data flow

```text
Demo generator or Finnhub quote API
                 |
             QuoteCache ---------> STOMP /topic/quotes
                 |  quote event (queued, never waits on a fill)
                 v
  REST ----> MatchingEngine: one thread + one OrderBook per symbol
                 |  plan fills from the book, then one transaction
                 v
     OrderExecution: lock every account involved, in ID order
                 |
     Postgres: orders, trades, cash, positions
                 |
          after commit: apply the changes to the book
                 |
          AFTER_COMMIT event ----> STOMP /topic/accounts/{id}
                                           |
                              React reloads portfolio/orders/trades
```

QuoteCache keeps an immutable snapshot of at most 240 observed quotes per symbol. The REST history endpoint hydrates charts after a browser refresh. Older updates are ignored, equal-timestamp updates replace the last sample, and live-mode seed prices are excluded. This bounded memory buffer resets on server restart.

The quote feed runs on Spring's scheduler, independently of browser connections. Each quote it publishes is queued to that symbol's matcher thread and the feed moves on, so a slow fill for one symbol never delays quotes for the others.

## Who trades with whom

Two kinds of liquidity meet in each book:

- **Other users.** Limit orders that do not fill straight away rest in the book and wait for a counterparty.
- **The house**, a simulated market maker. It quotes a bid and an ask either side of the feed price (`papertrader.house.spread-bps`, 10 basis points across the spread by default) and takes any size at those prices, so a market order always fills and a single user can trade alone.

An incoming order walks the opposite side of the book best price first, oldest first within a price, and trades at each resting order's limit. It stops at its own limit, and at the first level the house beats: the house takes any size, so paying a user more than the house asks would be a bad fill. At an equal price the user goes first. Whatever is left trades with the house if its price is within the limit; a limit order with shares still left rests, partly filled. A market order is refused while the quote is stale, because there is no house price to bound what it could cost.

Every quote also moves the house. Resting bids at or above the new house ask, and asks at or below the new house bid, fill in full at the house price.

Users can only meet inside the spread: an order priced through the house trades with the house first. With a zero spread every order trades with the house, which is how the integration tests keep their arithmetic exact.

An order that would trade with the same account's resting order is refused, and the account is asked to cancel the resting order first. Partly filled orders stay OPEN with a filled quantity and an average fill price; cancelling one keeps what filled.

## The order book

Each side of a book is a `TreeMap` from price to a level: bids sorted highest first, asks lowest first. A level is a `LinkedHashMap` from order ID to entry, which keeps arrival order, removes a cancelled order in O(1), and lets a partial fill replace an entry without losing its place in the queue. Matching touches only the levels it fills from, instead of scanning every open order.

Why a sorted map and not a heap: a heap gives the best price cheaply, but cancelling from the middle is O(n), and walking several levels means repeated pops. The sorted map does both directly.

## One matcher per symbol

Each symbol has a single-threaded executor that owns its book. Placements, cancels and quote-driven fills for that symbol all run on it, one at a time. Request threads hand their order over and wait for the result (up to 10 seconds, then 503), so within a symbol nothing can interleave: there is no lock on the book and no race between a fill and a cancel to reason about. Different symbols match in parallel on their own threads.

The request thread holds no transaction while it waits. Only the matcher thread opens one, so waiting requests cannot use up the connection pool.

### Postgres stays the record

The book is an index of what might fill; it never decides that something did. A placement first plans its fills from the book without changing it, then runs one transaction that re-reads each resting order it plans to trade with and fills it only if the row is still OPEN with enough shares left. The transaction reports what it changed (shares taken from an order, an order removed, a new order resting), and the engine applies that to the book only after commit. A rollback, such as a rejection for insufficient cash, leaves the book exactly as it was.

If a row ever disagrees with the book, the transaction throws, the engine reloads that symbol's book from its OPEN rows and retries once. The same reload builds every book on startup, followed by a sweep against the cached quotes.

This is not built for exchange-scale throughput. A larger system would partition symbols across processes, journal orders and quotes to a durable stream, and replay the book from it instead of from the orders table.

## Transactions and races

A trade between two users changes two accounts in one transaction: both cash balances, both positions, both orders and a trade row for each side. Every transaction locks the accounts it touches in ascending ID order. Two matcher threads can trade between the same pair of accounts at once, one on each symbol; because both take the locks in the same order, neither can hold the lock the other is waiting for. A test runs exactly that cross-symbol pattern repeatedly.

Accounts also carry a `@Version` column. With the row locks in place it never conflicts; it exists so that any future write path that forgets the lock fails with an optimistic-lock error instead of silently overwriting a balance.

Portfolio and leaderboard reads use repeatable-read transactions to avoid mixing balances and positions from different commits. The database refuses to overfill an order: `filled_quantity` is checked against `quantity`, and a FILLED order must be fully filled.

Open limit orders reserve buying power and inventory for the shares they still have to fill. The holds are not stored: placement reads the account's OPEN orders under its lock and subtracts their remaining buy notionals (at the limit price) from cash and their remaining sell quantities from shares. A buy reserves at its limit and fills at or below it, so its hold always covers the fill. A market buy is checked against the house ask, the worst price it can fill at. Each fill still re-checks the balance under the lock; a resting order that cannot pay (only possible for orders placed before reservations existed) is rejected and skipped.

Notional values round half up to cents per fill; prices and average costs use four decimals. Fills are whole-share and long-only.

## Safe retries

The optional idempotency key is scoped to the account and limited to 128 characters. Placement looks up an existing key before planning fills or checking balances. Retries of the same order run on the same matcher thread one after another, so the second always sees the first. Matching payloads return the existing order (including its latest status); changed payloads return 409. A partial unique index provides a second line of defense.

The browser retains a failed request's key until that payload changes or succeeds. Account switching clears pending retries. This prevents a network timeout followed by a retry from creating two fills.

## Feed modes and failures

- Demo: deterministic bounded price oscillations around configured seed prices, every 15 seconds. Clearly labeled simulated prices.
- Static: unchanged configured prices, used by integration tests.
- Finnhub: server-side HTTP requests with five-second connection/read timeouts. Seeds have an epoch timestamp and are never executable. Invalid, future-dated, or out-of-order quotes cannot replace good data. While a quote is older than 120 seconds the house is out: market orders are refused, and limit orders rest until a fresh quote crosses them. Users can still trade with each other.

Provider failures log only the symbol and exception class, retaining the last good data. The browser displays quote timestamps. Credentials are environment configuration and are never sent to the browser. Actual Finnhub account access is not exercised by CI.

## Real-time delivery

Only committed order changes are published. Clients subscribe to the same-origin STOMP endpoint; inbound client SEND frames are ignored because this channel is receive-only. Account events carry account ID, order ID, status and filled quantity, prompting a REST refresh; both sides of a user-to-user trade get one. Quote events carry symbol, price, and timestamp.

The simple broker has no durable replay. Reconnecting clients reload current REST state, and periodic refresh repairs missed messages. One app instance is the supported deployment; multiple instances would need shared quote and messaging infrastructure.

## Local demo boundary

There is deliberately no identity/authentication layer. Account IDs select shared practice accounts; they are not credentials. The Compose ports bind to 127.0.0.1. Do not expose this app publicly without authentication, account-level authorization for REST/STOMP, request limits, and a deployment-specific secret configuration.

## Checks

JUnit/Testcontainers exercises real Postgres migrations and transactional behavior, including concurrent orders, idempotent retries, user-to-user fills across several resting orders and the house, partial fills and cancels, self-trade refusal, and two matcher threads trading between the same accounts at once. The book and engine have unit tests for price-time priority, house priority and when book changes are applied. A scheduled integration test changes a quote and waits for the matcher to fill without another order request. Playwright runs against the packaged app, including two independently subscribed browser tabs, cancellation, server validation, and mobile overflow checks.

## Frontend navigation

The root route serves the product home page. The trading workspace lives at `/terminal`; `/terminal/` is also supported. The backend forwards only these explicit terminal routes to the bundled index, so direct navigation and refresh work without swallowing unknown API or asset routes. The terminal logo returns home. Instrument links pass a validated symbol query parameter, and the saved practice account remains in local storage across navigation.

The home page uses a dark charcoal layout with blue accents with live cache snapshots in a semantic market table. Account-opening links use `/terminal?setup=1`; successful account selection clears that parameter so refreshes resume the selected account instead of reopening setup.
