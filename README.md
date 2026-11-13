# PaperTrader

[![Build and test](https://github.com/nishantshah0/paper-trader/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/nishantshah0/paper-trader/actions/workflows/ci.yml) · [MIT license](LICENSE)

A local paper-trading simulator with a React terminal, a Spring Boot matching engine, and PostgreSQL. Practice market and limit orders with virtual cash, trade with other users inside a simulated market maker's spread, watch partial fills update across browser tabs, and track realized and unrealized P&L.

**No real money or brokerage connection.** The default feed generates simulated prices. Optional Finnhub mode uses provider quotes and refuses execution against stale prices. Practice accounts are shared and unauthenticated; this project is intended for local use.

![A resting limit order filling on its own as the price drops](docs/dashboard.gif)

A buy limit for 10 AAPL at $232.15 rests in the order book until the simulated price falls through it, then fills without another request. Cash, P&L, positions and trade history update over the WebSocket.

<details>
<summary>Home page and terminal screenshots</summary>

![PaperTrader home page](docs/home.png)

![PaperTrader trading terminal](docs/dashboard.png)

</details>

## Quick start

With Docker Desktop's engine running:

```bash
git clone https://github.com/nishantshah0/paper-trader.git
cd paper-trader
docker compose --profile app up --build -d --wait
```

Open **http://localhost:8080**. Choose **Open practice account**, enter a lowercase account name, and receive $100,000 in virtual funds. Keep the account ID to reopen it later. The terminal is at **http://localhost:8080/terminal**.

The first build downloads Java and Node dependencies. The app and database bind to loopback. Stop them while keeping account data with:

```bash
docker compose --profile app down
```

## Features

- Dark home page with current quote previews, direct account setup, and a guide to the simulator.
- Trading terminal with a searchable watchlist, chart crosshair and time ranges, order ticket, and keyboard-accessible activity tabs.
- Market and limit orders that match against other users' resting orders in price-time priority, fill in parts, and trade the rest with a simulated market maker.
- A live order book for each symbol, with the house's bid and ask merged into the users' price levels.
- Cancellation, cash/position validation, self-trade prevention, and account-scoped idempotency keys.
- Scheduled execution and committed-order notifications over STOMP WebSockets, with reconnect resynchronization and a polling fallback.
- Portfolio value, realized/unrealized P&L, execution history, and a leaderboard ranked by return percentage.
- Durable open and partly filled orders that resume after an application restart. Recent chart data holds up to 240 observed quotes per symbol; it survives browser refreshes and resets on server restart.

## Execution rules

Each symbol has two sources of liquidity: other users' resting limit orders, and the **house**, a simulated market maker that quotes a bid and an ask either side of the feed price (10 basis points across by default, `HOUSE_SPREAD_BPS`) and takes any size at those prices.

An incoming order trades with resting user orders first, best price first and oldest first within a price, at each resting order's limit. It stops at its own limit and at any level the house beats, then trades whatever is left with the house if the house price is within its limit. A limit order with shares still left rests in the book, partly filled. A market order always fills completely. Users meet inside the spread: an order priced through the house trades with the house.

When the feed moves, resting bids at or above the new house ask and asks at or below the new house bid fill in full at the house price. An order that would trade with the same account's resting order is refused.

Open limit orders reserve what they still need: a buy holds its limit price times its remaining quantity, a sell holds its remaining shares. A new order can only use buying power and shares that are not already held, so a resting order stays funded until it fills or is cancelled. Cancelling a partly filled order keeps what filled.

Each symbol has one matcher thread that owns its book and runs every placement, cancel and quote-driven fill for it in turn, so symbols match in parallel and nothing within a symbol interleaves. A trade between two users commits both accounts' cash, positions, orders and trade rows together, with the accounts locked in ID order so concurrent trades between the same accounts on different symbols cannot deadlock. Postgres is the record: the book changes only after its transaction commits, and is reloaded from open orders on startup or if the two ever disagree. Reusing an `Idempotency-Key` with the same account and payload returns the original order; a changed payload returns 409.

Fills are whole-share and long-only, with no commissions, slippage beyond the house spread, or shorting. See [architecture and tradeoffs](docs/architecture.md) for the book, the threading model and the race handling.

## Quote modes

| Mode             | Behavior                                                             |
| ---------------- | -------------------------------------------------------------------- |
| `demo` (default) | Simulated price updates every 15 seconds, clearly labeled in the UI. |
| `static`         | Configured prices remain unchanged for deterministic experiments.    |
| `finnhub`        | Server-side provider quotes. Requires your own API key.              |

For Finnhub, copy `.env.example` to `.env` and set:

```dotenv
FEED_MODE=finnhub
FINNHUB_API_KEY=your_key
FEED_INTERVAL_MS=15000
```

Recreate the app with the Quick start Compose command. Keys remain server-side. The adapter calls [Finnhub's quote endpoint](https://finnhub.io/docs/api/quote); twelve symbols at the default interval use up to 48 requests per minute. Set the interval to fit your provider plan.

Live mode never executes against seed prices. Provider timestamps must be within the last 120 seconds. Errors retain the last quote, but the house does not trade on a stale quote, including outside market hours: market orders are refused, limit orders rest until a fresh quote crosses them, and users can still trade with each other. Automated tests do not contact Finnhub.

## Development

Use **JDK 21**, **Node 22**, npm, and Docker. Maven is supplied by the wrapper; `.nvmrc` records the Node version.

```bash
# Backend: Spring Boot starts the Compose database.
./mvnw spring-boot:run

# Frontend, in another terminal:
cd frontend
npm ci
npm run dev
```

On Windows, use `.\mvnw.cmd` instead of `./mvnw`. Open Vite's displayed URL; it proxies REST and WebSocket traffic to port 8080. Stop the packaged app container before starting the backend locally.

## Verification

```bash
./mvnw verify
cd frontend
npm ci
npm run format:check
npm run build
npx playwright install chromium
npm test
```

Backend tests use real Postgres through Testcontainers. Browser tests require the complete app at `http://127.0.0.1:8080`; set `E2E_BASE_URL` for another local instance. They create synthetic accounts, so use a disposable database when a clean practice environment matters.

Coverage includes user-to-user and partial fills, price-time and house priority, self-trade refusal, concurrent trades between the same accounts on two symbols, scheduled limit fills, double-spend and fill/cancel races, idempotent retries, stale quotes, migrations, P&L, home/terminal navigation, account persistence, responsive layouts, and two-tab WebSocket delivery. CI runs on pull requests and main, and cancels obsolete runs on the same ref.

## API

All bodies are JSON. Errors use RFC 9457 problem details. Cash uses decimal cents; prices and average costs use four decimal places. Values are USD.

| Method | Path                                    | Purpose                                  |
| ------ | --------------------------------------- | ---------------------------------------- |
| POST   | `/api/accounts`                         | Create a practice account                |
| GET    | `/api/accounts/{id}`                    | Cash and starting balance                |
| GET    | `/api/accounts/{id}/portfolio`          | Positions, value, and P&L                |
| POST   | `/api/accounts/{id}/orders`             | Place an order; optional Idempotency-Key |
| GET    | `/api/accounts/{id}/orders?status=OPEN` | Orders; status is optional               |
| GET    | `/api/accounts/{id}/orders/{orderId}`   | One order                                |
| DELETE | `/api/accounts/{id}/orders/{orderId}`   | Cancel an open order                     |
| GET    | `/api/accounts/{id}/trades`             | Fills, newest first                      |
| GET    | `/api/quotes`, `/api/quotes/{symbol}`   | Cached quotes and timestamps             |
| GET    | `/api/quotes/status`                    | Feed mode                                |
| GET    | `/api/quotes/history`                   | Up to 240 observed quotes per symbol     |
| GET    | `/api/book/{symbol}`                    | Top 10 price levels and the house quote  |
| GET    | `/api/leaderboard`                      | Top 20 by return percentage              |
| GET    | `/actuator/health`                      | Health check                             |

Example limit-order body:

```json
{
    "symbol": "AAPL",
    "side": "BUY",
    "type": "LIMIT",
    "quantity": 2,
    "limitPrice": 225.5
}
```

STOMP endpoint: `/ws`. Subscribe to `/topic/quotes` and `/topic/accounts/{id}`. Account events trigger portfolio/order/trade refreshes after commit. Browser clients cannot publish broker messages.

## Limitations

There is no authentication or account-level authorization. Account IDs are not passwords. Add an identity and authorization layer before public deployment. Corporate actions, exchange calendars, persistent historical prices, and distributed messaging are outside this demo. The leaderboard scans accounts, and order lists are unpaginated; this is designed for a small local practice environment.

## What I would do next

- **A finite house.** The house takes any size at its quote. Giving it a displayed size per tick would let large orders walk the book and make the spread move with demand.
- **Order types.** Stop orders, time-in-force (IOC, FOK, day orders) and amend-in-place.
- **Persist quote history.** Chart history lives in memory and resets on restart.
- **Authentication.** Accounts are open by ID, which is fine locally and not for a public deployment.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for setup, checks, and change guidelines. Use the repository's issue forms for reproducible bugs and focused feature requests.

## License

[MIT](LICENSE)
