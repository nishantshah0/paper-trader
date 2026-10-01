# Architecture and execution guarantees

## Data flow

```text
Demo generator or Finnhub quote API
                 |
             QuoteCache ---------> STOMP /topic/quotes
                 |
  REST ----> OrderService <---- scheduled MatchingEngine
                 |
       account row lock + transaction
                 |
     Postgres: orders, trades, cash, positions
                 |
          AFTER_COMMIT event ----> STOMP /topic/accounts/{id}
                                           |
                              React reloads portfolio/orders/trades
```

QuoteCache keeps an immutable snapshot of at most 240 observed quotes per symbol. The REST history endpoint hydrates charts after a browser refresh. Older updates are ignored, equal-timestamp updates replace the last sample, and live-mode seed prices are excluded. This bounded memory buffer resets on server restart.

The quote feed and matcher use Spring's scheduler. They run independently of browser connections. Each candidate match invokes a separate proxied transactional service call; a failure for one account does not stop the remaining candidates.

## Why a database-backed book

The initial roadmap proposed an in-memory order book. For a small simulator, the existing partial index on open orders plus a fresh query each second is simpler to recover and reason about. The database remains the only authoritative order state. An application restart resumes matching existing OPEN orders; no cache hydration or after-commit insertion race is possible.

This is not intended for exchange-scale throughput. A larger system would page/batch candidates, partition symbols, introduce a durable quote/event stream, and define fairness and reservation rules explicitly.

## Transactions and races

Every placement, fill, and cancellation locks the account row first, then reads current order/position state. That shared lock order serializes competing operations on one account. Two workers seeing the same open order cannot both fill it: the second obtains the lock only after the first commits and then observes a terminal status.

Fills change cash, position, order status, and the trade row in one transaction. A unique trade/order index is a database backstop. Portfolio and leaderboard reads use repeatable-read transactions to avoid mixing balances and positions from different commits.

Open limit orders do not reserve buying power or inventory. This is an explicit simplification. A resting order can become unfunded before a crossing; it transitions to REJECTED. Market-order failures roll back the new order entirely.

Orders execute all-or-nothing at the current quote, even if its price improves on the limit. There are no partial fills or user-to-user matching. Notional values round half up to cents; prices and average cost use four decimals.

## Safe retries

The optional idempotency key is scoped to the account and limited to 128 characters. Under the account lock, placement looks up an existing key before checking current quotes or balances. Matching payloads return the existing order (including its latest status); changed payloads return 409. A partial unique index provides a second line of defense.

The browser retains a failed request's key until that payload changes or succeeds. Account switching clears pending retries. This prevents a network timeout followed by a retry from creating two fills.

## Feed modes and failures

- Demo: deterministic bounded price oscillations around configured seed prices, every 15 seconds. Clearly labeled simulated prices.
- Static: unchanged configured prices, used by integration tests.
- Finnhub: server-side HTTP requests with five-second connection/read timeouts. Seeds have an epoch timestamp and are never executable. Invalid, future-dated, or out-of-order quotes cannot replace good data. Quotes older than 120 seconds cannot execute.

Provider failures log only the symbol and exception class, retaining the last good data. The browser displays quote timestamps. Credentials are environment configuration and are never sent to the browser. Actual Finnhub account access is not exercised by CI.

## Real-time delivery

Only committed order changes are published. Clients subscribe to the same-origin STOMP endpoint; inbound client SEND frames are ignored because this channel is receive-only. Account events carry account ID, order ID, and status, prompting a REST refresh. Quote events carry symbol, price, and timestamp.

The simple broker has no durable replay. Reconnecting clients reload current REST state, and periodic refresh repairs missed messages. One app instance is the supported deployment; multiple instances would need shared quote and messaging infrastructure.

## Local demo boundary

There is deliberately no identity/authentication layer. Account IDs select shared practice accounts; they are not credentials. The Compose ports bind to 127.0.0.1. Do not expose this app publicly without authentication, account-level authorization for REST/STOMP, request limits, and a deployment-specific secret configuration.

## Checks

JUnit/Testcontainers exercises real Postgres migrations and transactional behavior, including concurrent orders and idempotent retries. A scheduled integration test changes a quote and waits for the matcher to fill without another order request. Playwright runs against the packaged app, including two independently subscribed browser tabs, cancellation, server validation, and mobile overflow checks.

## Frontend navigation

The root route serves the product home page. The trading workspace lives at `/terminal`; `/terminal/` is also supported. The backend forwards only these explicit terminal routes to the bundled index, so direct navigation and refresh work without swallowing unknown API or asset routes. The terminal logo returns home. Instrument links pass a validated symbol query parameter, and the saved practice account remains in local storage across navigation.

The home page uses a separate light navy-and-white layout with live cache snapshots in a semantic market table. Account-opening links use `/terminal?setup=1`; successful account selection clears that parameter so refreshes resume the selected account instead of reopening setup.
