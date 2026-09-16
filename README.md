# paper-trader

A paper-trading simulator: a Spring Boot service with a hand-written limit-order matching engine, a live market-data feed, WebSocket push, and a React dashboard.

> **No real money.** Every account starts with synthetic cash. The service never connects to a brokerage, never places real orders, and cannot move funds. The prices are real; everything else is simulated.

## Status

Week 1 in progress. In place: the Spring Boot skeleton, Postgres via Docker Compose, the Flyway schema for users, accounts, orders, trades, and positions, and a Testcontainers test that proves the migrations apply to a real Postgres. Next: REST endpoints and market orders.

## What it will do

- Live quotes for a small set of symbols, polled server-side from a free market-data API and cached per symbol. The browser never calls the API directly.
- Market orders fill immediately at the cached price.
- Limit orders rest in an in-memory order book per symbol and fill when the cached price crosses them.
- Every fill writes a trade row and updates a position row. Cash and positions live in Postgres; the order book lives in memory and is rebuilt from open orders on startup.
- Realized and unrealized profit and loss per position, and a leaderboard by account value.
- Quotes, fills, and portfolio updates pushed to connected browsers over STOMP WebSockets.

## Architecture

```
market-data API
      |  polled on a schedule, cached per symbol
      v
 price feed --quote events--> matching engine --fills--> Postgres
                              in-memory order book       accounts, orders,
                              per symbol                 trades, positions
                                    |
                                    |  quotes, fills, portfolio updates
                                    v
                             STOMP WebSocket --> React dashboard
```

## Data model

Five tables, owned by Flyway migrations in `src/main/resources/db/migration`.

- `users` and `accounts`: one account per user, holding `cash` and `starting_cash` so the leaderboard can rank by return.
- `orders`: `MARKET` or `LIMIT`, `BUY` or `SELL`, with a status of `OPEN`, `FILLED`, `CANCELLED`, or `REJECTED`. A check constraint requires a limit price exactly when the type is `LIMIT`. A partial index on open orders backs the order-book rebuild on startup.
- `trades`: one row per fill, with the executed price and quantity.
- `positions`: one row per account and symbol, with quantity, average cost, and realized profit and loss. Positions are long-only.

Money and prices are `numeric`, never floating point.

## Stack

| Layer | Choice |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 4, Spring Data JPA, Spring WebSocket (STOMP) |
| Database | PostgreSQL 17 with Flyway migrations |
| Tests | JUnit 5, Testcontainers against a real Postgres |
| Front end | React |
| Packaging | Docker Compose, GitHub Actions |

## Running it

You need Docker (Docker Desktop or any engine with Compose) and JDK 21. Maven is not required; the wrapper downloads it on first use. On Windows, use `mvnw.cmd` instead of `./mvnw`.

```bash
./mvnw spring-boot:run
```

Spring Boot's Docker Compose support starts the Postgres service from `compose.yaml` on its own, then Flyway applies the migrations. The service listens on http://localhost:8080 and reports health at http://localhost:8080/actuator/health.

Tests run against a throwaway Postgres started by Testcontainers, so Docker must be running:

```bash
./mvnw test
```

## Roadmap

- [ ] **Week 1, core domain.** Docker Compose, Flyway, REST endpoints for accounts, portfolio, and orders. Market orders only. Done when a market order placed over curl changes cash and positions.
- [ ] **Week 2, the engine.** Limit orders, a per-symbol order book, a scheduled price feed, the matching engine, and P&L. Done when a resting limit order fills on its own when the price crosses it, with a test that proves it.
- [ ] **Week 3, real-time and front end.** WebSocket push and the React dashboard: portfolio, order ticket, open orders, trade history, price line, leaderboard. Done when two browser tabs see the same fill at the same moment.
- [ ] **Week 4, hardening and packaging.** Validation and error responses, optimistic locking on balances, idempotency keys on order placement, one-command Docker Compose, CI, and architecture notes. Done when a stranger can clone it and have it running in five minutes.

## License

[MIT](LICENSE)
