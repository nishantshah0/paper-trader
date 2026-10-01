import { useCallback, useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";
import { api, money, signed } from "./api";

const companies = {
  AAPL: "Apple",
  AMD: "Advanced Micro Devices",
  AMZN: "Amazon",
  COST: "Costco",
  GOOGL: "Alphabet",
  JPM: "JPMorgan Chase",
  META: "Meta Platforms",
  MSFT: "Microsoft",
  NFLX: "Netflix",
  NVDA: "NVIDIA",
  TSLA: "Tesla",
  V: "Visa",
};
const initialAccount = () => {
  try {
    return localStorage.getItem("paper-trader.account") || "";
  } catch {
    return "";
  }
};
function Chart({ points }) {
  if (points.length < 2)
    return (
      <div className="chart-empty">
        Collecting quotes… the price line appears after the next update.
      </div>
    );
  const values = points.map((p) => Number(p.price));
  const min = Math.min(...values),
    max = Math.max(...values),
    spread = Math.max(max - min, 0.01);
  const line = values
    .map((v, i) =>
      [(i / (values.length - 1)) * 700, 140 - ((v - min) / spread) * 115].join(
        ",",
      ),
    )
    .join(" ");
  return (
    <svg
      className="chart"
      viewBox="0 0 700 160"
      role="img"
      aria-label="Price movement during this session"
      preserveAspectRatio="none"
    >
      <defs>
        <linearGradient id="fade" x1="0" y1="0" x2="0" y2="1">
          <stop stopColor="#93dbb1" stopOpacity=".25" />
          <stop offset="1" stopColor="#93dbb1" stopOpacity="0" />
        </linearGradient>
      </defs>
      <path
        d={"M " + line.replaceAll(" ", " L ") + " L 700 160 L 0 160 Z"}
        fill="url(#fade)"
      />
      <polyline
        points={line}
        fill="none"
        stroke="#a9e5c0"
        strokeWidth="2.5"
        vectorEffect="non-scaling-stroke"
      />
    </svg>
  );
}
export default function App() {
  const [account, setAccount] = useState(initialAccount),
    [portfolio, setPortfolio] = useState(null);
  const [quotes, setQuotes] = useState([]),
    [orders, setOrders] = useState([]),
    [trades, setTrades] = useState([]),
    [leaders, setLeaders] = useState([]);
  const [mode, setMode] = useState(""),
    [connected, setConnected] = useState(false),
    [history, setHistory] = useState({});
  const [symbol, setSymbol] = useState("AAPL"),
    [side, setSide] = useState("BUY"),
    [type, setType] = useState("MARKET");
  const [quantity, setQuantity] = useState("1"),
    [limit, setLimit] = useState(""),
    [busy, setBusy] = useState(false);
  const [username, setUsername] = useState(""),
    [existing, setExisting] = useState(""),
    [error, setError] = useState(""),
    [notice, setNotice] = useState("");
  const accountRef = useRef(account),
    refreshSequence = useRef(0),
    pending = useRef(null);
  accountRef.current = account;
  const recordQuotes = useCallback((list) => {
    setQuotes((prev) => {
      const map = new Map(prev.map((q) => [q.symbol, q]));
      list.forEach((q) => map.set(q.symbol, q));
      return [...map.values()].sort((a, b) => a.symbol.localeCompare(b.symbol));
    });
    setHistory((prev) => {
      const next = { ...prev };
      list.forEach((q) => {
        const a = prev[q.symbol] || [];
        if (a.at(-1)?.asOf !== q.asOf) next[q.symbol] = [...a, q].slice(-60);
      });
      return next;
    });
  }, []);
  const refresh = useCallback(async () => {
    const id = accountRef.current,
      seq = ++refreshSequence.current;
    const result = await Promise.all([
      api("/quotes"),
      api("/leaderboard"),
      ...(id
        ? [
            api("/accounts/" + id + "/portfolio"),
            api("/accounts/" + id + "/orders"),
            api("/accounts/" + id + "/trades"),
          ]
        : []),
    ]);
    if (seq !== refreshSequence.current || id !== accountRef.current) return;
    recordQuotes(result[0]);
    setLeaders(result[1]);
    if (id) {
      setPortfolio(result[2]);
      setOrders(result[3]);
      setTrades(result[4]);
    }
  }, [recordQuotes]);
  useEffect(() => {
    api("/quotes/status")
      .then((s) => setMode(s.mode))
      .catch((e) => setError(e.message));
    refresh().catch((e) => setError(e.message));
    const timer = setInterval(() => refresh().catch(() => {}), 15000);
    return () => clearInterval(timer);
  }, [refresh]);
  useEffect(() => {
    setPortfolio(null);
    setOrders([]);
    setTrades([]);
    pending.current = null;
    refresh().catch((e) => setError(e.message));
    const client = new Client({
      brokerURL:
        (location.protocol === "https:" ? "wss://" : "ws://") +
        location.host +
        "/ws",
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true);
        refresh().catch(() => {});
        client.subscribe("/topic/quotes", (m) => {
          recordQuotes([JSON.parse(m.body)]);
          refresh().catch(() => {});
        });
        if (account)
          client.subscribe("/topic/accounts/" + account, () =>
            refresh().catch(() => {}),
          );
      },
      onWebSocketClose: () => setConnected(false),
      onStompError: () => setConnected(false),
    });
    client.activate();
    return () => {
      setConnected(false);
      client.deactivate();
    };
  }, [account, refresh, recordQuotes]);
  const chooseAccount = (id) => {
    setAccount(String(id));
    try {
      localStorage.setItem("paper-trader.account", String(id));
    } catch {}
    setError("");
    setNotice("");
  };
  async function create(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const a = await api("/accounts", {
        method: "POST",
        body: JSON.stringify({ username }),
      });
      chooseAccount(a.id);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function load(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await api("/accounts/" + existing);
      chooseAccount(existing);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setNotice("");
    const payload = {
      symbol,
      side,
      type,
      quantity: Number(quantity),
      ...(type === "LIMIT" ? { limitPrice: Number(limit) } : {}),
    };
    const body = JSON.stringify(payload);
    if (pending.current?.body !== body)
      pending.current = { body, key: crypto.randomUUID() };
    try {
      const o = await api("/accounts/" + account + "/orders", {
        method: "POST",
        headers: { "Idempotency-Key": pending.current.key },
        body,
      });
      pending.current = null;
      setNotice("Order #" + o.id + " · " + o.status.toLowerCase());
      await refresh();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function cancel(id) {
    setBusy(true);
    setError("");
    try {
      await api("/accounts/" + account + "/orders/" + id, { method: "DELETE" });
      setNotice("Order #" + id + " cancelled");
      await refresh();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  const selected = quotes.find((q) => q.symbol === symbol),
    points = history[symbol] || [],
    openOrders = orders.filter((o) => o.status === "OPEN");
  return (
    <div className="shell">
      <header>
        <a className="brand" href="/" aria-label="Paper Trader home">
          <span className="brand-mark">p.</span>paper<span>trader</span>
        </a>
        <span className="workspace-label">THE PRACTICE DESK</span>
        <div className="connection">
          <i className={connected ? "online" : ""} />
          {connected ? "Connected" : "Reconnecting"}
        </div>
      </header>
      <div className="disclaimer">
        <span className="pill">
          {mode === "finnhub"
            ? "LIVE FEED"
            : mode === "static"
              ? "STATIC PRICES"
              : "DEMO FEED"}
        </span>
        <span>
          Practice with synthetic cash.{" "}
          {mode === "finnhub"
            ? "Quotes require a fresh market timestamp to trade."
            : "Prices are simulated."}{" "}
          No real orders.
        </span>
      </div>
      <main>
        <div className="page-title">
          <div>
            <p className="eyebrow">YOUR NEXT MOVE STARTS HERE</p>
            <h1>
              The trading desk<span>.</span>
            </h1>
            <p className="muted">A little practice. A clearer perspective.</p>
          </div>
          {portfolio && (
            <div className="account-badge">
              <span>{portfolio.username}</span>
              <small>Account #{account}</small>
              <button className="link" onClick={() => chooseAccount("")}>
                Switch account
              </button>
            </div>
          )}
        </div>
        {error && (
          <div role="alert" className="alert">
            {error}
            <button aria-label="Dismiss error" onClick={() => setError("")}>
              ×
            </button>
          </div>
        )}
        {notice && (
          <div role="status" className="notice">
            {notice}
          </div>
        )}
        {!portfolio && (
          <section className="onboarding panel">
            <div>
              <p className="eyebrow">A FRESH START</p>
              <h2>Your first $100,000 is on us.</h2>
              <p className="muted">
                Create a practice account, or reopen one by its ID.
              </p>
              <small>
                This is a shared demo. Account IDs are public, not passwords.
              </small>
            </div>
            <form onSubmit={create}>
              <label htmlFor="username">New account name</label>
              <div className="inline">
                <input
                  id="username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  required
                  minLength="3"
                  maxLength="32"
                  pattern="[a-z0-9_]+"
                  title="Lowercase letters, digits, and underscores"
                  placeholder="e.g. market_learner"
                />
                <button disabled={busy}>Create account</button>
              </div>
            </form>
            <form onSubmit={load}>
              <label htmlFor="account-id">Existing account ID</label>
              <div className="inline">
                <input
                  id="account-id"
                  type="number"
                  min="1"
                  step="1"
                  required
                  value={existing}
                  onChange={(e) => setExisting(e.target.value)}
                  placeholder="Account ID"
                />
                <button className="secondary" disabled={busy}>
                  Open account
                </button>
              </div>
            </form>
          </section>
        )}
        <section className="metrics" aria-label="Portfolio summary">
          {[
            ["Account value", portfolio?.totalValue],
            ["Available cash", portfolio?.cash],
            ["Unrealized P&L", portfolio?.unrealizedPnl],
            ["Realized P&L", portfolio?.realizedPnl],
          ].map(([label, value], i) => (
            <div className="metric" key={label}>
              <p>{label}</p>
              <strong
                className={
                  i > 1 && value != null
                    ? value >= 0
                      ? "positive"
                      : "negative"
                    : ""
                }
              >
                {value == null ? "—" : i > 1 ? signed(value) : money(value)}
              </strong>
              <small>
                {i === 0
                  ? "Cash + current holdings"
                  : i === 1
                    ? "Open orders do not reserve cash"
                    : i === 2
                      ? "On your open positions"
                      : "On your completed trades"}
              </small>
            </div>
          ))}
        </section>
        <div className="desk-grid">
          <section className="market panel">
            <div className="section-heading">
              <h2>Market watch</h2>
              <span className="muted">USD</span>
            </div>
            <div className="watchlist">
              {quotes.map((q) => (
                <button
                  key={q.symbol}
                  className={
                    "quote-row " + (symbol === q.symbol ? "selected" : "")
                  }
                  onClick={() => setSymbol(q.symbol)}
                >
                  <span className="ticker-icon">{q.symbol.slice(0, 1)}</span>
                  <span>
                    <b>{q.symbol}</b>
                    <small>{companies[q.symbol] || q.symbol}</small>
                  </span>
                  <strong>{money(q.price)}</strong>
                </button>
              ))}
            </div>
          </section>
          <div className="center">
            <section className="price-panel panel">
              <div className="section-heading">
                <div>
                  <p className="eyebrow">{companies[symbol] || symbol}</p>
                  <h2>
                    {symbol} <span className="muted">/ USD</span>
                  </h2>
                </div>
                <span className="pill">SESSION</span>
              </div>
              <div className="current-price">
                {selected ? money(selected.price) : "—"}
              </div>
              <Chart points={points} />
              <div className="chart-caption">
                <span>
                  Last {Math.min(points.length, 60)} quotes · this browser
                  session
                </span>
                <span>
                  {selected && new Date(selected.asOf).toLocaleTimeString()}
                </span>
              </div>
            </section>
            <section className="panel positions">
              <div className="section-heading">
                <h2>Your positions</h2>
                <span className="count">
                  {portfolio?.positions.length || 0}
                </span>
              </div>
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>Symbol</th>
                      <th>Shares</th>
                      <th>Avg. cost</th>
                      <th>Value</th>
                      <th>Unrealized</th>
                    </tr>
                  </thead>
                  <tbody>
                    {portfolio?.positions.map((p) => (
                      <tr key={p.symbol}>
                        <td>
                          <button
                            className="link"
                            onClick={() => setSymbol(p.symbol)}
                          >
                            {p.symbol}
                          </button>
                        </td>
                        <td>{p.quantity}</td>
                        <td>{money(p.avgCost)}</td>
                        <td>{money(p.marketValue)}</td>
                        <td
                          className={
                            p.unrealizedPnl >= 0 ? "positive" : "negative"
                          }
                        >
                          {signed(p.unrealizedPnl)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {!portfolio?.positions.length && (
                <p className="empty">
                  Your portfolio starts with a first trade.
                </p>
              )}
            </section>
          </div>
          <section className="ticket panel">
            <div className="section-heading">
              <h2>Place an order</h2>
              <span className="ticket-icon">↗</span>
            </div>
            <form onSubmit={submit}>
              <div className="segmented">
                {["BUY", "SELL"].map((s) => (
                  <button
                    key={s}
                    type="button"
                    aria-pressed={side === s}
                    className={side === s ? "active" : ""}
                    onClick={() => setSide(s)}
                  >
                    {s === "BUY" ? "Buy" : "Sell"}
                  </button>
                ))}
              </div>
              <label htmlFor="symbol">Symbol</label>
              <select
                id="symbol"
                value={symbol}
                onChange={(e) => setSymbol(e.target.value)}
              >
                {quotes.map((q) => (
                  <option key={q.symbol}>{q.symbol}</option>
                ))}
              </select>
              <label htmlFor="order-type">Order type</label>
              <select
                id="order-type"
                value={type}
                onChange={(e) => setType(e.target.value)}
              >
                <option value="MARKET">Market order</option>
                <option value="LIMIT">Limit order</option>
              </select>
              <label htmlFor="quantity">Quantity</label>
              <input
                id="quantity"
                type="number"
                min="1"
                max="1000000"
                step="1"
                required
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
              />
              {type === "LIMIT" && (
                <>
                  <label htmlFor="limit">Limit price</label>
                  <input
                    id="limit"
                    type="number"
                    min="0.0001"
                    max="9999999999"
                    step="0.0001"
                    required
                    value={limit}
                    onChange={(e) => setLimit(e.target.value)}
                  />
                </>
              )}
              <div className="estimate">
                <span>Estimated total</span>
                <strong>
                  {money(
                    Number(quantity) *
                      (type === "LIMIT"
                        ? Number(limit)
                        : Number(selected?.price || 0)),
                  )}
                </strong>
              </div>
              <button
                className="submit-order"
                disabled={busy || !portfolio || !selected}
              >
                {busy
                  ? "Working…"
                  : (side === "BUY" ? "Buy " : "Sell ") + symbol}
              </button>
              <p className="ticket-note">
                {type === "LIMIT"
                  ? "Fills when the quote crosses your limit. Funds are checked again at execution."
                  : "Fills at the latest available quote. Whole shares only."}
              </p>
            </form>
          </section>
        </div>
        <div className="bottom-grid">
          <section className="panel">
            <div className="section-heading">
              <h2>Open orders</h2>
              <span className="count">{openOrders.length}</span>
            </div>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Symbol</th>
                    <th>Side</th>
                    <th>Shares</th>
                    <th>Limit</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {openOrders.map((o) => (
                    <tr key={o.id}>
                      <td>{o.symbol}</td>
                      <td>{o.side}</td>
                      <td>{o.quantity}</td>
                      <td>{money(o.limitPrice)}</td>
                      <td>
                        <button
                          className="link"
                          disabled={busy}
                          onClick={() => cancel(o.id)}
                        >
                          Cancel #{o.id}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {!openOrders.length && (
              <p className="empty">No orders waiting for a price.</p>
            )}
          </section>
          <section className="panel">
            <div className="section-heading">
              <h2>Leaderboard</h2>
              <span className="muted">By return</span>
            </div>
            {leaders.slice(0, 5).map((l, i) => (
              <div className="leader" key={l.accountId}>
                <span className="rank">{String(i + 1).padStart(2, "0")}</span>
                <div>
                  <b>{l.username}</b>
                  <small>{money(l.totalValue)}</small>
                </div>
                <strong
                  className={l.returnPercent >= 0 ? "positive" : "negative"}
                >
                  {l.returnPercent >= 0 ? "+" : ""}
                  {Number(l.returnPercent).toFixed(2)}%
                </strong>
              </div>
            ))}
            {!leaders.length && (
              <p className="empty">The first spot is waiting for you.</p>
            )}
          </section>
        </div>
        <section className="panel history">
          <div className="section-heading">
            <h2>Trade history</h2>
            <span className="muted">Most recent first</span>
          </div>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Executed</th>
                  <th>Symbol</th>
                  <th>Side</th>
                  <th>Shares</th>
                  <th>Fill price</th>
                </tr>
              </thead>
              <tbody>
                {trades.slice(0, 50).map((t) => (
                  <tr key={t.id}>
                    <td>{new Date(t.executedAt).toLocaleString()}</td>
                    <td>{t.symbol}</td>
                    <td>
                      <span className={"side " + t.side.toLowerCase()}>
                        {t.side}
                      </span>
                    </td>
                    <td>{t.quantity}</td>
                    <td>{money(t.price)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!trades.length && (
            <p className="empty">Completed trades will appear here.</p>
          )}
        </section>
        {orders.some((o) => o.status === "REJECTED") && (
          <section className="notice">
            {orders.filter((o) => o.status === "REJECTED").length} resting
            order(s) rejected because cash or shares were unavailable at
            execution.
          </section>
        )}
      </main>
      <footer>
        <span>papertrader · Built for the learning curve.</span>
        <span>Simulation only · No brokerage connection</span>
      </footer>
    </div>
  );
}
