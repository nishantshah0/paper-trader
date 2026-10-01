import { useEffect, useState } from "react";
import Icon from "./Icon";
import { api, money } from "./api";
const names = {
  AAPL: "Apple Inc.",
  NVDA: "NVIDIA Corporation",
  MSFT: "Microsoft Corporation",
  AMZN: "Amazon.com Inc.",
  GOOGL: "Alphabet Inc.",
  JPM: "JPMorgan Chase & Co.",
};
const faqs = [
  [
    "Am I trading with real money?",
    "No. Every account starts with virtual cash. Orders are simulated, and PaperTrader has no connection to a brokerage or the ability to move real funds.",
  ],
  [
    "Where do the prices come from?",
    "This app starts with a clearly labeled simulated price feed. An optional live-feed configuration uses provider quotes. Check the feed label in the terminal to see which mode is active.",
  ],
  [
    "How do I create or reopen an account?",
    "Launch the terminal and choose a lowercase account name. Save the account ID shown in the top-right corner to reopen it later. Practice accounts are shared in this demo; account IDs are not private sign-in credentials.",
  ],
  [
    "What happens to my limit order?",
    "A buy limit executes when the quote is at or below your limit. A sell limit executes at or above it. Cash and shares are checked again at execution; an unfunded order is rejected. You can cancel an open order from the activity panel.",
  ],
  [
    "How close is this to real trading?",
    "This is a learning simulator. It uses whole-share, whole-order fills and does not model liquidity, slippage, commissions, shorting, or corporate actions. Simulated results are not a prediction of real-market performance.",
  ],
];

export default function Home() {
  const [menu, setMenu] = useState(false),
    [quotes, setQuotes] = useState([]),
    [mode, setMode] = useState("");
  useEffect(() => {
    let active = true;
    async function refresh() {
      try {
        const [q, s] = await Promise.all([
          api("/quotes"),
          api("/quotes/status"),
        ]);
        if (active) {
          setQuotes(q);
          setMode(s.mode);
        }
      } catch {
        if (active) setMode("unavailable");
      }
    }
    refresh();
    const timer = setInterval(refresh, 15000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, []);
  const close = () => setMenu(false);
  const feedLabel =
    mode === "finnhub"
      ? "Provider quotes"
      : mode === "static"
        ? "Static prices"
        : mode === "unavailable"
          ? "Feed unavailable"
          : mode
            ? "Simulated prices"
            : "Connecting";
  return (
    <div className="home">
      <a className="home-skip" href="#home-main">
        Skip to content
      </a>
      <div className="broker-utility">
        <div>
          <span>Paper trading simulator</span>
          <span>
            Virtual funds only <i /> No brokerage connection
          </span>
        </div>
      </div>
      <header className="home-nav">
        <a href="/" className="home-brand" aria-label="Paper Trader home">
          <Icon name="chart" size={27} />
          <strong>PaperTrader</strong>
        </a>
        <nav
          className={menu ? "home-links open" : "home-links"}
          aria-label="Main navigation"
          id="mobile-menu-content"
        >
          <a href="#platform" onClick={close}>
            Platform
          </a>
          <a href="#markets" onClick={close}>
            Markets
          </a>
          <a href="#how-it-works" onClick={close}>
            Getting started
          </a>
          <a href="#questions" onClick={close}>
            FAQ
          </a>
        </nav>
        <a href="/terminal" className="nav-launch">
          Launch terminal
          <Icon name="arrow" size={15} />
        </a>
        <button
          className="home-menu"
          aria-label="Toggle navigation"
          aria-expanded={menu}
          aria-controls="mobile-menu-content"
          onClick={() => setMenu((v) => !v)}
        >
          {menu ? <Icon name="close" /> : <span aria-hidden="true">☰</span>}
        </button>
      </header>
      <div className="home-market-strip">
        <div className="market-strip-inner">
          <span className="strip-feed">
            <i />
            {feedLabel}
          </span>
          {["AAPL", "NVDA", "MSFT", "AMZN", "GOOGL"].map((symbol) => {
            const q = quotes.find((q) => q.symbol === symbol);
            return (
              <a
                className="strip-quote"
                href={"/terminal?symbol=" + symbol}
                key={symbol}
              >
                <b>{symbol}</b>
                <span>
                  {q && new Date(q.asOf).getTime() > 0 ? money(q.price) : "—"}
                </span>
              </a>
            );
          })}
        </div>
      </div>
      <main id="home-main">
        <section className="broker-hero">
          <div className="broker-hero-inner">
            <div className="broker-introduction">
              <span className="home-eyebrow">PAPER TRADING</span>
              <h1>
                Practice trading <br />
                with virtual funds.
              </h1>
              <p>
                $100,000 in virtual buying power. Place market and limit orders,
                track your positions, and review every execution.
              </p>
              <div className="hero-actions">
                <a className="home-cta" href="/terminal?setup=1">
                  Open practice account
                  <Icon name="chevron" size={15} />
                </a>
                <a className="home-secondary" href="/terminal">
                  Explore the terminal
                </a>
              </div>
              <div className="hero-fine">
                No real money or brokerage account required.
              </div>
            </div>
            <div className="broker-product">
              <div className="product-label">
                <span>PaperTrader Terminal</span>
                <span>Platform preview</span>
              </div>
              <a
                className="terminal-preview"
                href="/terminal"
                aria-label="Open the trading terminal"
              >
                <img
                  src="/terminal-preview.png"
                  alt="Trading terminal showing a watchlist, price chart, order ticket, and portfolio positions"
                  width="1440"
                  height="1148"
                />
              </a>
              <p className="preview-caption">
                Illustrative terminal view. Prices and balances shown are
                simulated.
              </p>
            </div>
          </div>
        </section>
        <div className="broker-specifications">
          <div>
            <span>Starting balance</span>
            <strong>$100,000 virtual</strong>
          </div>
          <div>
            <span>Markets</span>
            <strong>12 US equities</strong>
          </div>
          <div>
            <span>Order types</span>
            <strong>Market and limit</strong>
          </div>
          <div>
            <span>Account tools</span>
            <strong>Positions, P&L, trade history</strong>
          </div>
        </div>
        <section id="markets" className="broker-section market-overview">
          <div className="market-content">
            <div className="section-title">
              <h2>Market overview</h2>
              <span
                className={
                  "market-feed " + (mode === "unavailable" ? "offline" : "")
                }
              >
                <i />
                {feedLabel}
              </span>
            </div>
            <p className="section-description">
              Select an instrument to open its chart and order ticket.
            </p>
            <div className="broker-market-table">
              <table aria-label="Available market quotes">
                <thead>
                  <tr>
                    <th>Instrument</th>
                    <th>Last price</th>
                    <th>Quote time</th>
                    <th>
                      <span className="sr-only">Open instrument</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {Object.entries(names).map(([symbol, name]) => {
                    const q = quotes.find((q) => q.symbol === symbol);
                    const valid = q && new Date(q.asOf).getTime() > 0;
                    return (
                      <tr key={symbol}>
                        <td>
                          <a
                            className="home-quote"
                            href={"/terminal?symbol=" + symbol}
                          >
                            <b>{symbol}</b>
                            <small>{name}</small>
                          </a>
                        </td>
                        <td className="market-price">
                          {valid ? money(q.price) : "—"}
                        </td>
                        <td className="quote-time">
                          {valid
                            ? new Date(q.asOf).toLocaleTimeString([], {
                                hour: "2-digit",
                                minute: "2-digit",
                                second: "2-digit",
                              })
                            : "—"}
                        </td>
                        <td>
                          <a
                            className="quote-action"
                            href={"/terminal?symbol=" + symbol}
                            aria-label={"Open " + symbol + " in terminal"}
                          >
                            <span>View</span>
                            <Icon name="chevron" size={13} />
                          </a>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <p className="market-footnote">
              Displayed prices are for this practice environment. Check the feed
              label before placing an order.
            </p>
          </div>
          <aside className="account-access">
            <Icon name="wallet" size={25} />
            <h3>Your practice account</h3>
            <p>
              Start with virtual funds and access the full trading workspace.
            </p>
            <ul>
              <li>Market and limit orders</li>
              <li>Position and P&L tracking</li>
              <li>A record of every execution</li>
            </ul>
            <a className="home-cta" href="/terminal?setup=1">
              Create practice account
              <Icon name="chevron" size={14} />
            </a>
            <div className="returning-account">
              <span>Already have an account?</span>
              <a href="/terminal">
                Continue to terminal
                <Icon name="arrow" size={13} />
              </a>
            </div>
          </aside>
        </section>
        <section id="platform" className="broker-platform">
          <div className="broker-section">
            <div className="section-heading-row">
              <div>
                <span className="home-eyebrow">TRADING PLATFORM</span>
                <h2>The tools to manage every trade.</h2>
              </div>
              <a href="/terminal" className="home-text-link">
                View the terminal
                <Icon name="arrow" size={15} />
              </a>
            </div>
            <div className="broker-features">
              {[
                [
                  "Charts and watchlists",
                  "Follow observed price movements, compare instruments, and switch directly from the watchlist to your order ticket.",
                  "chart",
                ],
                [
                  "Order management",
                  "Execute at the current quote or specify a limit price. Review, track, and cancel open orders from one place.",
                  "activity",
                ],
                [
                  "Portfolio reporting",
                  "See your available cash, positions, and realized and unrealized P&L, with a complete execution history.",
                  "wallet",
                ],
              ].map(([title, text, icon]) => (
                <article key={title}>
                  <Icon name={icon} size={23} />
                  <h3>{title}</h3>
                  <p>{text}</p>
                </article>
              ))}
            </div>
          </div>
        </section>
        <section id="how-it-works" className="broker-section broker-how">
          <div className="section-heading-row">
            <div>
              <span className="home-eyebrow">GETTING STARTED</span>
              <h2>Open an account and place a practice order.</h2>
            </div>
          </div>
          <ol>
            {[
              [
                "Create your account",
                "Choose a practice account name. Your virtual starting balance is available immediately.",
              ],
              [
                "Select an instrument",
                "Open a chart from the market list and enter the quantity and order type.",
              ],
              [
                "Review your activity",
                "Track your order status and review the impact on your positions and buying power.",
              ],
            ].map(([title, text], i) => (
              <li key={title}>
                <span>{i + 1}</span>
                <div>
                  <h3>{title}</h3>
                  <p>{text}</p>
                </div>
              </li>
            ))}
          </ol>
        </section>
        <section id="questions" className="broker-section broker-faq">
          <div>
            <span className="home-eyebrow">SUPPORT</span>
            <h2>Frequently asked questions</h2>
            <p>About the practice account and how orders are simulated.</p>
          </div>
          <div className="faq-list">
            {faqs.map(([q, a]) => (
              <details key={q}>
                <summary>
                  <span>{q}</span>
                  <span className="faq-plus" aria-hidden="true">
                    +
                  </span>
                </summary>
                <p>{a}</p>
              </details>
            ))}
          </div>
        </section>
        <div className="broker-final">
          <div>
            <h2>Start with a practice account.</h2>
            <p>
              Use virtual funds to become familiar with the trading workflow.
            </p>
          </div>
          <a className="home-cta" href="/terminal?setup=1">
            Open practice account
            <Icon name="chevron" size={15} />
          </a>
        </div>
      </main>
      <footer className="home-footer">
        <div className="footer-main">
          <div>
            <a href="/" className="home-brand">
              <Icon name="chart" size={25} />
              <strong>PaperTrader</strong>
            </a>
            <p>A local paper-trading simulator.</p>
          </div>
          <nav aria-label="Footer navigation">
            <a href="/terminal">Terminal</a>
            <a href="#markets">Markets</a>
            <a href="#how-it-works">Getting started</a>
            <a href="#questions">FAQ</a>
            <a
              href="https://github.com/nishantshah0/paper-trader"
              target="_blank"
              rel="noreferrer"
            >
              Source code ↗
            </a>
          </nav>
        </div>
        <div className="footer-disclosure">
          <p>
            PaperTrader is a simulation. No real funds are held and no orders
            are sent to a brokerage. Fills do not model slippage, liquidity, or
            commissions. Practice results do not predict real-market
            performance.
          </p>
          <p>
            Practice accounts are shared in this demo. Account IDs are not
            private credentials.
          </p>
        </div>
      </footer>
    </div>
  );
}
