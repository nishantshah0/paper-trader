import { useCallback, useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";
import { api, money, signed } from "./api";
import Chart from "./Chart";
import Icon from "./Icon";

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
function mergeHistory(previous, samples) {
  const next = { ...previous };
  for (const sample of samples) {
    if (new Date(sample.asOf).getTime() <= 0) continue;
    const list = next[sample.symbol] || [];
    const byTime = new Map(list.map((q) => [q.asOf, q]));
    byTime.set(sample.asOf, sample);
    next[sample.symbol] = [...byTime.values()]
      .sort((a, b) => new Date(a.asOf) - new Date(b.asOf))
      .slice(-240);
  }
  return next;
}
const percent = (value) => (value >= 0 ? "+" : "") + value.toFixed(2) + "%";
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
  const [symbol, setSymbol] = useState(() => {
      const requested = new URLSearchParams(location.search)
        .get("symbol")
        ?.toUpperCase();
      return requested && Object.hasOwn(companies, requested)
        ? requested
        : "AAPL";
    }),
    [side, setSide] = useState("BUY"),
    [type, setType] = useState("MARKET");
  const [quantity, setQuantity] = useState("1"),
    [limit, setLimit] = useState(""),
    [busy, setBusy] = useState(false);
  const [username, setUsername] = useState(""),
    [existing, setExisting] = useState(""),
    [error, setError] = useState(""),
    [notice, setNotice] = useState("");
  const [search, setSearch] = useState(""),
    [activeTab, setActiveTab] = useState("positions");
  const [timeframe, setTimeframe] = useState("ALL"),
    [accountModal, setAccountModal] = useState(
      new URLSearchParams(location.search).get("setup") === "1",
    );
  const dialog = useRef(null);
  useEffect(() => {
    if (accountModal) dialog.current?.showModal();
    else dialog.current?.close();
  }, [accountModal]);
  useEffect(() => {
    api("/quotes/history")
      .then((data) =>
        setHistory((prev) => mergeHistory(prev, Object.values(data).flat())),
      )
      .catch(() => {});
  }, []);
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
    setHistory((prev) => mergeHistory(prev, list));
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
    setAccountModal(false);
    const nextUrl = new URL(window.location.href);
    nextUrl.searchParams.delete("setup");
    window.history.replaceState(
      null,
      "",
      nextUrl.pathname + nextUrl.search + nextUrl.hash,
    );
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
      setActiveTab(o.status === "OPEN" ? "orders" : "positions");
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
    allPoints = history[symbol] || [];
  const cutoff =
    timeframe === "ALL"
      ? 0
      : new Date(allPoints.at(-1)?.asOf || Date.now()).getTime() -
        Number(timeframe) * 60000;
  const points = allPoints.filter((q) => new Date(q.asOf).getTime() >= cutoff);
  const first = points[0]?.price,
    change =
      first && selected
        ? (Number(selected.price) / Number(first) - 1) * 100
        : 0;
  const openOrders = orders.filter((o) => o.status === "OPEN"),
    filtered = quotes.filter((q) =>
      (q.symbol + " " + companies[q.symbol])
        .toLowerCase()
        .includes(search.toLowerCase()),
    );
  const estimate =
    Number(quantity) *
    (type === "LIMIT" ? Number(limit) : Number(selected?.price || 0));
  const held =
    portfolio?.positions.find((p) => p.symbol === symbol)?.quantity || 0;
  const modeLabel =
    mode === "finnhub"
      ? "LIVE QUOTES"
      : mode === "static"
        ? "STATIC QUOTES"
        : "SIMULATED QUOTES";
  const historyChange = (q) => {
    const start = history[q.symbol]?.[0]?.price;
    return start ? (Number(q.price) / Number(start) - 1) * 100 : 0;
  };
  const accountForms = (
    <>
      <div className="account-intro">
        <span className="eyebrow">PRACTICE ACCOUNT</span>
        <h2>Your capital. Zero risk.</h2>
        <p>Start with $100,000 in simulated buying power.</p>
      </div>
      <div className="account-forms">
        <form onSubmit={create}>
          <label htmlFor="username">New account name</label>
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
          <button className="primary" disabled={busy}>
            Create account
          </button>
        </form>
        <form onSubmit={load}>
          <label htmlFor="account-id">Existing account ID</label>
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
        </form>
      </div>
      <p className="account-note">
        Shared demo · Account IDs are public, not passwords.
      </p>
    </>
  );
  return (
    <div className="terminal">
      <header className="topbar">
        <a className="brand" href="/" aria-label="Paper Trader home">
          <span className="brand-logo">
            <Icon name="chart" size={22} />
          </span>
          <strong>
            PAPER<span>TRADER</span>
          </strong>
          <span className="version">TERMINAL</span>
        </a>
        <span className="header-divider" />
        <span className="workspace">
          <Icon name="grid" size={14} />
          Trading workspace
        </span>
        <div className="topbar-right">
          <span className="environment">PAPER ACCOUNT</span>
          <button
            className="account-toggle"
            onClick={() => setAccountModal(true)}
          >
            <span className="avatar">
              {portfolio?.username.slice(0, 1).toUpperCase() || "P"}
            </span>
            <span>
              {portfolio?.username || "Open an account"}
              <small>
                {portfolio ? "Account #" + account : "Start trading"}
              </small>
            </span>
            <Icon name="chevron" size={13} />
          </button>
        </div>
      </header>
      <div className="ticker-tape" aria-label="Market ticker">
        <span className="tape-label">
          <span className="pulse-dot" />
          {modeLabel}
        </span>
        {quotes.slice(0, 7).map((q) => (
          <button key={q.symbol} onClick={() => setSymbol(q.symbol)}>
            <b>{q.symbol}</b>
            <span>{money(q.price)}</span>
            <em className={historyChange(q) >= 0 ? "positive" : "negative"}>
              {percent(historyChange(q))}
            </em>
          </button>
        ))}
      </div>
      <main>
        <div className="workspace-heading">
          <div>
            <span className="eyebrow">WORKSPACE /</span>
            <h1>Trading terminal</h1>
          </div>
          <div className="connection">
            <i className={connected ? "online" : ""} />
            <span>{connected ? "Connected" : "Reconnecting"}</span>
            <span className="mode-label">
              {mode === "finnhub" ? "Provider feed" : "Demo environment"}
            </span>
          </div>
        </div>
        {!portfolio && !account && !accountModal && (
          <section className="onboarding">{accountForms}</section>
        )}
        <section className="account-strip" aria-label="Portfolio summary">
          {[
            ["Total equity", portfolio?.totalValue, "USD"],
            ["Buying power", portfolio?.cash, "AVAILABLE"],
            ["Unrealized P&L", portfolio?.unrealizedPnl, "OPEN POSITIONS"],
            ["Realized P&L", portfolio?.realizedPnl, "CLOSED TRADES"],
          ].map(([label, value, hint], i) => (
            <div className="metric" key={label}>
              <span>{label}</span>
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
              <small>{hint}</small>
            </div>
          ))}
          <div className="account-return">
            <span>Account return</span>
            <strong
              className={portfolio?.totalPnl < 0 ? "negative" : "positive"}
            >
              {portfolio
                ? percent(
                    (Number(portfolio.totalPnl) /
                      Number(portfolio.startingCash)) *
                      100,
                  )
                : "—"}
            </strong>
            <span className="return-icon">
              <Icon name="activity" size={34} />
            </span>
          </div>
        </section>
        {error && (
          <div className="alert" role="alert">
            <span>{error}</span>
            <button aria-label="Dismiss error" onClick={() => setError("")}>
              <Icon name="close" size={15} />
            </button>
          </div>
        )}
        {notice && (
          <div className="notice" role="status">
            <span className="pulse-dot" />
            {notice}
            <button
              aria-label="Dismiss notification"
              onClick={() => setNotice("")}
            >
              <Icon name="close" size={14} />
            </button>
          </div>
        )}
        <div className="terminal-grid">
          <aside className="watch-panel panel">
            <div className="panel-heading">
              <h2>Watchlist</h2>
              <span className="count">{quotes.length}</span>
            </div>
            <label className="search">
              <Icon name="search" size={14} />
              <input
                aria-label="Search symbols"
                placeholder="Search symbol"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </label>
            <div className="watch-columns">
              <span>Symbol / Name</span>
              <span>Price / Change</span>
            </div>
            <div className="watchlist">
              {filtered.map((q) => (
                <button
                  className={
                    "quote-row " + (symbol === q.symbol ? "selected" : "")
                  }
                  key={q.symbol}
                  onClick={() => setSymbol(q.symbol)}
                >
                  <span>
                    <b>{q.symbol}</b>
                    <small>{companies[q.symbol]}</small>
                  </span>
                  <span>
                    <strong>{money(q.price)}</strong>
                    <small
                      className={
                        historyChange(q) >= 0 ? "positive" : "negative"
                      }
                    >
                      {percent(historyChange(q))}
                    </small>
                  </span>
                </button>
              ))}
              {!filtered.length && (
                <p className="empty">No matching symbols.</p>
              )}
            </div>
            <div className="watch-foot">
              <Icon name="clock" size={12} />
              Change since first observed quote
            </div>
          </aside>
          <div className="center-column">
            <section className="chart-panel panel">
              <div className="instrument-header">
                <span className="instrument-logo">{symbol.slice(0, 1)}</span>
                <div>
                  <h2>
                    {symbol} <span>{companies[symbol]}</span>
                  </h2>
                  <small>
                    US EQUITY <i /> USD <i />{" "}
                    {mode === "finnhub" ? "LIVE FEED" : "PAPER TRADING"}
                  </small>
                </div>
                <span className="instrument-more">{modeLabel}</span>
              </div>
              <div className="price-summary">
                <strong>{selected ? money(selected.price) : "—"}</strong>
                <span className={change >= 0 ? "positive" : "negative"}>
                  {change >= 0 ? "↗" : "↘"} {percent(change)}
                  <small> observed window</small>
                </span>
              </div>
              <div className="chart-toolbar">
                <div role="group" aria-label="Chart time range">
                  {[
                    ["5", "5m"],
                    ["15", "15m"],
                    ["60", "1h"],
                    ["ALL", "All"],
                  ].map(([value, label]) => (
                    <button
                      key={value}
                      aria-pressed={timeframe === value}
                      className={timeframe === value ? "active" : ""}
                      onClick={() => setTimeframe(value)}
                    >
                      {label}
                    </button>
                  ))}
                </div>
                <span>
                  <Icon name="chart" size={14} />
                  Price line
                  <span className="chart-divider" />
                  USD
                </span>
              </div>
              <Chart key={symbol + timeframe} points={points} symbol={symbol} />
              <div className="chart-footer">
                <span>
                  <span className="pulse-dot" />{" "}
                  {mode === "finnhub"
                    ? "Provider quotes"
                    : "Simulated price feed"}
                </span>
                <span>
                  {selected
                    ? "Last update " +
                      new Date(selected.asOf).toLocaleTimeString()
                    : "Connecting…"}
                </span>
              </div>
            </section>
            <section className="ledger panel">
              <div
                className="ledger-tabs"
                role="tablist"
                aria-label="Account activity"
                onKeyDown={(e) => {
                  const keys = ["positions", "orders", "history", "all"];
                  if (
                    !["ArrowLeft", "ArrowRight", "Home", "End"].includes(e.key)
                  )
                    return;
                  e.preventDefault();
                  const index = keys.indexOf(activeTab);
                  const next =
                    e.key === "Home"
                      ? 0
                      : e.key === "End"
                        ? 3
                        : (index + (e.key === "ArrowRight" ? 1 : 3)) % 4;
                  setActiveTab(keys[next]);
                  document.getElementById("tab-" + keys[next])?.focus();
                }}
              >
                {[
                  ["positions", "Positions", portfolio?.positions.length || 0],
                  ["orders", "Open orders", openOrders.length],
                  ["history", "Trade history", trades.length],
                  ["all", "All orders", orders.length],
                ].map(([key, label, count]) => (
                  <button
                    role="tab"
                    aria-selected={activeTab === key}
                    tabIndex={activeTab === key ? 0 : -1}
                    aria-controls={"panel-" + key}
                    id={"tab-" + key}
                    key={key}
                    onClick={() => setActiveTab(key)}
                  >
                    {label}
                    <span>{count}</span>
                  </button>
                ))}
              </div>
              {activeTab === "positions" && (
                <section
                  className="positions"
                  role="tabpanel"
                  id="panel-positions"
                  aria-labelledby="tab-positions"
                >
                  <div className="table-scroll">
                    <table>
                      <thead>
                        <tr>
                          <th>Instrument</th>
                          <th>Quantity</th>
                          <th>Avg. price</th>
                          <th>Market value</th>
                          <th>Unrealized P&L</th>
                        </tr>
                      </thead>
                      <tbody>
                        {portfolio?.positions.map((p) => (
                          <tr key={p.symbol}>
                            <td>
                              <button
                                className="symbol-link"
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
                    <div className="empty-state">
                      <Icon name="wallet" size={23} />
                      <b>No open positions</b>
                      <span>Your portfolio starts with a first trade.</span>
                    </div>
                  )}
                </section>
              )}
              {activeTab === "orders" && (
                <section
                  role="tabpanel"
                  id="panel-orders"
                  aria-labelledby="tab-orders"
                >
                  <div className="table-scroll">
                    <table>
                      <thead>
                        <tr>
                          <th>Instrument</th>
                          <th>Side</th>
                          <th>Quantity</th>
                          <th>Limit price</th>
                          <th>Action</th>
                        </tr>
                      </thead>
                      <tbody>
                        {openOrders.map((o) => (
                          <tr key={o.id}>
                            <td>{o.symbol}</td>
                            <td>
                              <span className={"side " + o.side.toLowerCase()}>
                                {o.side}
                              </span>
                            </td>
                            <td>{o.quantity}</td>
                            <td>{money(o.limitPrice)}</td>
                            <td>
                              <button
                                className="cancel-order"
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
                    <div className="empty-state">
                      <Icon name="clock" size={23} />
                      <b>No working orders</b>
                      <span>Your limit orders will appear here.</span>
                    </div>
                  )}
                </section>
              )}
              {activeTab === "history" && (
                <section
                  className="history"
                  role="tabpanel"
                  id="panel-history"
                  aria-labelledby="tab-history"
                >
                  <div className="table-scroll">
                    <table>
                      <thead>
                        <tr>
                          <th>Time</th>
                          <th>Instrument</th>
                          <th>Side</th>
                          <th>Quantity</th>
                          <th>Fill price</th>
                        </tr>
                      </thead>
                      <tbody>
                        {trades.slice(0, 50).map((t) => (
                          <tr key={t.id}>
                            <td>
                              {new Date(t.executedAt).toLocaleTimeString()}
                            </td>
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
                    <div className="empty-state">
                      <Icon name="activity" size={23} />
                      <b>No executions yet</b>
                      <span>Completed trades appear here.</span>
                    </div>
                  )}
                </section>
              )}
              {activeTab === "all" && (
                <section
                  role="tabpanel"
                  id="panel-all"
                  aria-labelledby="tab-all"
                >
                  <div className="table-scroll">
                    <table>
                      <thead>
                        <tr>
                          <th>Order</th>
                          <th>Symbol</th>
                          <th>Side</th>
                          <th>Quantity</th>
                          <th>Status</th>
                        </tr>
                      </thead>
                      <tbody>
                        {orders.slice(0, 50).map((o) => (
                          <tr key={o.id}>
                            <td>#{o.id}</td>
                            <td>{o.symbol}</td>
                            <td>{o.side}</td>
                            <td>{o.quantity}</td>
                            <td>
                              <span
                                className={
                                  "order-status " + o.status.toLowerCase()
                                }
                              >
                                {o.status}
                              </span>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  {!orders.length && (
                    <div className="empty-state">
                      <Icon name="activity" size={23} />
                      <b>No orders yet</b>
                      <span>Submit your first order from the ticket.</span>
                    </div>
                  )}
                </section>
              )}
            </section>
          </div>
          <aside className="right-column">
            <section className="order-panel panel">
              <div className="panel-heading">
                <h2>Order entry</h2>
                <span className="paper-label">PAPER</span>
              </div>
              <form onSubmit={submit}>
                <div className="segmented">
                  {["BUY", "SELL"].map((s) => (
                    <button
                      type="button"
                      key={s}
                      aria-pressed={side === s}
                      className={side === s ? "active " + s.toLowerCase() : ""}
                      onClick={() => setSide(s)}
                    >
                      {s === "BUY" ? "Buy" : "Sell"}
                    </button>
                  ))}
                </div>
                <div className="field">
                  <label htmlFor="symbol">Instrument</label>
                  <select
                    id="symbol"
                    value={symbol}
                    onChange={(e) => setSymbol(e.target.value)}
                  >
                    {quotes.map((q) => (
                      <option key={q.symbol}>{q.symbol}</option>
                    ))}
                  </select>
                </div>
                <div className="field">
                  <label htmlFor="order-type">Order type</label>
                  <select
                    id="order-type"
                    value={type}
                    onChange={(e) => setType(e.target.value)}
                  >
                    <option value="MARKET">Market</option>
                    <option value="LIMIT">Limit</option>
                  </select>
                </div>
                <div className="field">
                  <label htmlFor="quantity">
                    Quantity<span aria-hidden="true">SHARES</span>
                  </label>
                  <input
                    id="quantity"
                    type="number"
                    required
                    min="1"
                    max="1000000"
                    step="1"
                    value={quantity}
                    onChange={(e) => setQuantity(e.target.value)}
                  />
                </div>
                {type === "LIMIT" && (
                  <div className="field">
                    <label htmlFor="limit">
                      Limit price<span aria-hidden="true">USD</span>
                    </label>
                    <input
                      id="limit"
                      type="number"
                      required
                      min="0.0001"
                      max="9999999999"
                      step="0.0001"
                      value={limit}
                      onChange={(e) => setLimit(e.target.value)}
                    />
                  </div>
                )}
                <div className="quantity-shortcuts">
                  {[1, 5, 10, 25].map((q) => (
                    <button
                      type="button"
                      key={q}
                      onClick={() => setQuantity(String(q))}
                    >
                      {q} shares
                    </button>
                  ))}
                </div>
                <div className="order-summary">
                  <div>
                    <span>Market price</span>
                    <b>{selected ? money(selected.price) : "—"}</b>
                  </div>
                  <div>
                    <span>
                      {side === "BUY" ? "Buying power" : "Shares available"}
                    </span>
                    <b>
                      {side === "BUY"
                        ? portfolio
                          ? money(portfolio.cash)
                          : "—"
                        : held}
                    </b>
                  </div>
                  <div className="order-total">
                    <span>Estimated total</span>
                    <b>{money(estimate)}</b>
                  </div>
                </div>
                <button
                  className={"submit-order " + side.toLowerCase()}
                  disabled={busy || !portfolio || !selected}
                >
                  {busy
                    ? "Submitting…"
                    : (side === "BUY" ? "Buy " : "Sell ") + symbol}
                  <Icon name="arrow" size={15} />
                </button>
                <p className="order-note">
                  {type === "LIMIT"
                    ? "Executes when the quote crosses your limit. Cash and shares are checked at execution."
                    : "Executes at the latest quote. Whole shares only. No commissions."}
                </p>
              </form>
            </section>
            <section className="leaderboard panel">
              <div className="panel-heading">
                <h2>Top traders</h2>
                <span className="muted">RETURN</span>
              </div>
              {leaders.slice(0, 4).map((l, i) => (
                <div className="leader" key={l.accountId}>
                  <span className="rank">{String(i + 1).padStart(2, "0")}</span>
                  <span title={l.username}>
                    {l.username}
                    <small>{money(l.totalValue)}</small>
                  </span>
                  <strong
                    className={l.returnPercent >= 0 ? "positive" : "negative"}
                  >
                    {percent(Number(l.returnPercent))}
                  </strong>
                </div>
              ))}
              {!leaders.length && (
                <p className="empty">Create an account to join the board.</p>
              )}
            </section>
          </aside>
        </div>
      </main>
      <footer>
        <span>
          <span className="pulse-dot" />
          All trades are simulated. No real funds.
        </span>
        <span>
          PAPERTRADER <i /> {quotes.length} INSTRUMENTS <i /> USD
        </span>
      </footer>
      <dialog
        ref={dialog}
        className="account-dialog"
        onCancel={() => setAccountModal(false)}
        onClick={(e) => {
          if (e.target === dialog.current) setAccountModal(false);
        }}
      >
        <button
          className="dialog-close"
          aria-label="Close account dialog"
          onClick={() => setAccountModal(false)}
        >
          <Icon name="close" />
        </button>
        {accountModal && accountForms}
        {error && <p className="negative">{error}</p>}
      </dialog>
    </div>
  );
}
