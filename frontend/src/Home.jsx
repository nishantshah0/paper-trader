import { useEffect, useState } from "react";
import Icon from "./Icon";
import { api, money } from "./api";
const names = { AAPL: "Apple", NVDA: "NVIDIA", MSFT: "Microsoft" };
const features = [
  {
    number: "01",
    icon: "chart",
    title: "A desk that means business.",
    text: "A focused chart, a searchable watchlist, and your order ticket in one workspace. Everything you need to make your next move.",
    tags: ["Interactive charts", "12 instruments"],
  },
  {
    number: "02",
    icon: "activity",
    title: "Your strategy. Your order.",
    text: "Enter at the current quote or set a limit and wait for your price. Follow working orders and cancel them as your plan changes.",
    tags: ["Market orders", "Limit orders"],
  },
  {
    number: "03",
    icon: "wallet",
    title: "See the whole picture.",
    text: "Track buying power, positions, and realized and unrealized P&L. Every execution leaves a record you can learn from.",
    tags: ["Portfolio tracking", "Trade history"],
  },
];
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
  return (
    <div className="home">
      <a className="home-skip" href="#home-main">
        Skip to content
      </a>
      <header className="home-nav">
        <a href="/" className="home-brand" aria-label="Paper Trader home">
          <span>
            <Icon name="chart" size={23} />
          </span>
          PAPER<span className="brand-light">TRADER</span>
        </a>
        <nav
          className={menu ? "home-links open" : "home-links"}
          aria-label="Main navigation"
        >
          <a href="#platform" onClick={close}>
            The platform
          </a>
          <a href="#how-it-works" onClick={close}>
            How it works
          </a>
          <a href="#questions" onClick={close}>
            FAQ
          </a>
        </nav>
        <a href="/terminal" className="nav-launch">
          Launch terminal
          <Icon name="arrow" size={16} />
        </a>
        <button
          className="home-menu"
          aria-label="Toggle navigation"
          aria-expanded={menu}
          onClick={() => setMenu((v) => !v)}
        >
          {menu ? <Icon name="close" /> : <span aria-hidden="true">☰</span>}
        </button>
      </header>
      <main id="home-main">
        <section className="home-hero">
          <div className="hero-orbit" aria-hidden="true" />
          <div className="hero-content">
            <p className="home-eyebrow">
              <span />
              THE MARKET IS YOUR CLASSROOM
            </p>
            <h1>
              Get a feel
              <br />
              for <em>the market.</em>
            </h1>
            <p className="hero-description">
              A real trading workspace. $100,000 in virtual cash.
              <br className="desktop-break" /> Room to practice every move.
            </p>
            <div className="hero-actions">
              <a className="home-cta" href="/terminal">
                Start practicing
                <Icon name="arrow" size={19} />
              </a>
              <a className="home-text-link" href="#how-it-works">
                See how it works<span aria-hidden="true">↓</span>
              </a>
            </div>
            <p className="hero-fine">
              <span className="checkmark">✓</span>No real money
              <span className="checkmark">✓</span>No brokerage connection
            </p>
          </div>
          <div className="hero-card">
            <div className="capital-heading">
              <span className="capital-icon">
                <Icon name="wallet" size={20} />
              </span>
              <span>YOUR STARTING CAPITAL</span>
              <span className="virtual-badge">VIRTUAL</span>
            </div>
            <div className="capital-value">
              $100,000<span>.00</span>
            </div>
            <div className="capital-caption">
              <span className="capital-dot" />
              Ready for your first trade.
            </div>
            <div className="capital-divider" />
            <div className="home-quotes-heading">
              <span>A FEW PLACES TO START</span>
              <span>
                {mode === "finnhub"
                  ? "LIVE QUOTES"
                  : mode === "static"
                    ? "STATIC QUOTES"
                    : mode === "unavailable"
                      ? "FEED OFFLINE"
                      : mode
                        ? "SIMULATED"
                        : "CONNECTING"}
              </span>
            </div>
            {["AAPL", "NVDA", "MSFT"].map((symbol, i) => {
              const q = quotes.find((q) => q.symbol === symbol);
              return (
                <a
                  className="home-quote"
                  href={"/terminal?symbol=" + symbol}
                  key={symbol}
                >
                  <span className={"company-icon company-" + i}>
                    {symbol.slice(0, 1)}
                  </span>
                  <span>
                    <b>{symbol}</b>
                    <small>{names[symbol]}</small>
                  </span>
                  <strong>{q ? money(q.price) : "—"}</strong>
                  <Icon name="chevron" size={14} />
                </a>
              );
            })}
            <a href="/terminal" className="all-instruments">
              Explore the trading desk
              <Icon name="arrow" size={14} />
            </a>
          </div>
        </section>
        <div className="home-facts">
          <div>
            <strong>
              100<span>k</span>
            </strong>
            <span>VIRTUAL STARTING CAPITAL</span>
          </div>
          <div>
            <strong>12</strong>
            <span>STOCKS TO EXPLORE</span>
          </div>
          <div>
            <strong>2</strong>
            <span>ORDER TYPES TO PRACTICE</span>
          </div>
          <div>
            <strong>0</strong>
            <span>REAL DOLLARS AT RISK</span>
          </div>
        </div>
        <section id="platform" className="home-platform home-section">
          <div className="section-kicker">
            <span>01 / THE PLATFORM</span>
            <span>BUILT FOR YOUR LEARNING CURVE</span>
          </div>
          <div className="home-section-heading">
            <h2>
              Your space
              <br />
              to figure it out.
            </h2>
            <p>
              Follow a price. Make a plan. Place an order.
              <br />A complete terminal that keeps your decisions
              <br className="desktop-break" /> and their outcomes in view.
            </p>
          </div>
          <a
            href="/terminal"
            className="terminal-preview"
            aria-label="Open the trading terminal"
          >
            <div className="preview-chrome">
              <span>
                <i />
                <i />
                <i />
              </span>
              <b>PAPERTRADER / WORKSPACE</b>
              <span className="preview-open">
                Open terminal
                <Icon name="arrow" size={13} />
              </span>
            </div>
            <img
              src="/terminal-preview.png"
              alt="PaperTrader terminal showing the watchlist, interactive price chart, order entry, and portfolio positions"
              loading="lazy"
              width="1440"
              height="1148"
            />
            <div className="preview-bottom">
              <span>THE FULL WORKSPACE. YOUR NEXT MOVE.</span>
              <span>
                Explore terminal <Icon name="arrow" size={18} />
              </span>
            </div>
          </a>
          <p className="preview-caption">
            Terminal preview · Prices and balances shown are simulated.
          </p>
          <div className="feature-grid">
            {features.map((f) => (
              <article key={f.number}>
                <div className="feature-top">
                  <span>{f.number}</span>
                  <Icon name={f.icon} size={24} />
                </div>
                <h3>{f.title}</h3>
                <p>{f.text}</p>
                <div className="feature-tags">
                  {f.tags.map((t) => (
                    <span key={t}>{t}</span>
                  ))}
                </div>
              </article>
            ))}
          </div>
        </section>
        <section id="how-it-works" className="home-how home-section">
          <div className="section-kicker">
            <span>02 / GETTING STARTED</span>
            <span>FROM CURIOUS TO HANDS-ON</span>
          </div>
          <div className="home-section-heading">
            <h2>
              Three steps.
              <br />A thousand things to learn.
            </h2>
            <a className="home-text-link" href="/terminal">
              Let's get started
              <Icon name="arrow" size={18} />
            </a>
          </div>
          <div className="steps-grid">
            {[
              [
                "01",
                "Make it yours.",
                "Create a practice account in the terminal. Your $100,000 virtual balance is ready immediately.",
              ],
              [
                "02",
                "Pick your moment.",
                "Explore the watchlist, follow the chart, and choose a market order or set your own limit price.",
              ],
              [
                "03",
                "Learn from the result.",
                "Watch orders execute, review your positions and P&L, and use your trade history to reflect on your decisions.",
              ],
            ].map(([n, title, text]) => (
              <article key={n}>
                <span className="step-number">{n}</span>
                <h3>{title}</h3>
                <p>{text}</p>
              </article>
            ))}
          </div>
        </section>
        <section id="questions" className="home-faq home-section">
          <div className="faq-intro">
            <p className="section-kicker">03 / GOOD QUESTIONS</p>
            <h2>
              Before your
              <br />
              first trade.
            </h2>
            <p>
              A few things to know about
              <br />
              your practice workspace.
            </p>
          </div>
          <div className="faq-list">
            {faqs.map(([q, a]) => (
              <details key={q}>
                <summary>
                  {q}
                  <span aria-hidden="true">+</span>
                </summary>
                <p>{a}</p>
              </details>
            ))}
          </div>
        </section>
        <section className="home-final">
          <span className="home-eyebrow">
            <span />
            YOUR WORKSPACE IS READY
          </span>
          <h2>
            Make your first move
            <br />
            <em>a practice move.</em>
          </h2>
          <a href="/terminal" className="home-cta">
            Launch your terminal
            <Icon name="arrow" size={20} />
          </a>
          <p>Virtual cash. Real curiosity.</p>
        </section>
      </main>
      <footer className="home-footer">
        <div>
          <a href="/" className="home-brand">
            <span>
              <Icon name="chart" size={21} />
            </span>
            PAPER<span className="brand-light">TRADER</span>
          </a>
          <p>A place to practice the market.</p>
        </div>
        <nav aria-label="Footer navigation">
          <a href="/terminal">Terminal</a>
          <a href="#how-it-works">How it works</a>
          <a href="#questions">FAQ</a>
          <a
            href="https://github.com/nishantshah0/paper-trader"
            target="_blank"
            rel="noreferrer"
          >
            GitHub ↗
          </a>
        </nav>
        <div className="footer-bottom">
          <span>SIMULATION ONLY · NO REAL FUNDS OR ORDERS</span>
          <span>Built for learning by doing.</span>
        </div>
      </footer>
    </div>
  );
}
