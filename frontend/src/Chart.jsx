import { useEffect, useRef, useState } from "react";
import { money } from "./api";
const time = (value) =>
  new Date(value).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
export default function Chart({ points, symbol }) {
  const [hover, setHover] = useState(null);
  const [width, setWidth] = useState(900);
  const container = useRef(null);
  useEffect(() => {
    const observer = new ResizeObserver((entries) =>
      setWidth(Math.max(320, entries[0].contentRect.width)),
    );
    observer.observe(container.current);
    return () => observer.disconnect();
  }, []);
  const values = points.map((p) => Number(p.price)),
    W = width,
    H = width < 500 ? 290 : 340,
    left = 18,
    right = W - 80,
    top = 25,
    bottom = H - 42;
  const low = values.length ? Math.min(...values) : 0,
    high = values.length ? Math.max(...values) : 1;
  const padding = Math.max((high - low) * 0.2, high * 0.0005, 0.01),
    min = low - padding,
    max = high + padding;
  const y = (v) => top + ((max - v) / (max - min)) * (bottom - top);
  const timestamps = points.map((p) => new Date(p.asOf).getTime());
  const start = timestamps[0] || 0,
    duration = Math.max(1, (timestamps.at(-1) || 0) - start);
  const x = (i) => left + ((timestamps[i] - start) / duration) * (right - left);
  const line = values.map((v, i) => x(i) + "," + y(v)).join(" ");
  const active = hover != null ? points[hover] : null;
  const last = values.at(-1),
    up = values.length < 2 || last >= values[0];
  const color = up ? "#37c996" : "#ed727b";
  function track(e) {
    const box = e.currentTarget.getBoundingClientRect();
    const px = ((e.clientX - box.left) / box.width) * W;
    const target = start + ((px - left) / (right - left)) * duration;
    const index = timestamps.reduce(
      (nearest, t, i) =>
        Math.abs(t - target) < Math.abs(timestamps[nearest] - target)
          ? i
          : nearest,
      0,
    );
    setHover(index);
  }
  return (
    <div className="chart-wrap" ref={container}>
      <div className="chart-readout">
        <span>{symbol}</span>
        <span>{active ? time(active.asOf) : "LATEST"}</span>
        <b style={{ color }}>{money(active?.price ?? last)}</b>
        <span className="chart-readout-end">
          {points.length} OBSERVED QUOTES
        </span>
      </div>
      <svg
        className="chart"
        viewBox={"0 0 " + W + " " + H}
        role="img"
        aria-label="Price movement during this session"
        onPointerMove={points.length ? track : undefined}
        onPointerLeave={() => setHover(null)}
        tabIndex={0}
        onKeyDown={(e) => {
          if (points.length && ["ArrowLeft", "ArrowRight"].includes(e.key)) {
            e.preventDefault();
            setHover((i) =>
              Math.max(
                0,
                Math.min(
                  points.length - 1,
                  (i ?? points.length - 1) + (e.key === "ArrowRight" ? 1 : -1),
                ),
              ),
            );
          }
        }}
      >
        <defs>
          <linearGradient id="chart-fill" x1="0" y1="0" x2="0" y2="1">
            <stop stopColor={color} stopOpacity=".17" />
            <stop offset="1" stopColor={color} stopOpacity="0" />
          </linearGradient>
        </defs>
        {[0, 1, 2, 3, 4].map((i) => {
          const v = max - (i * (max - min)) / 4,
            py = y(v);
          return (
            <g key={i}>
              <line
                x1={left}
                y1={py}
                x2={right}
                y2={py}
                className="chart-grid"
              />
              <text x={right + 16} y={py + 4} className="axis-label">
                {values.length ? v.toFixed(2) : "—"}
              </text>
            </g>
          );
        })}
        {(width < 500 ? [0, 2, 4] : [0, 1, 2, 3, 4]).map((i) => {
          const px = left + ((right - left) * i) / 4,
            index = Math.round(((points.length - 1) * i) / 4);
          return (
            <g key={i}>
              <line
                x1={px}
                y1={top}
                x2={px}
                y2={bottom}
                className="chart-grid vertical"
              />
              <text
                x={px}
                y={H - 12}
                textAnchor={i === 0 ? "start" : i === 4 ? "end" : "middle"}
                className="axis-label"
              >
                {points.length ? time(start + (duration * i) / 4) : "—"}
              </text>
            </g>
          );
        })}
        {values.length > 1 && (
          <>
            <path
              d={
                "M " +
                line.replaceAll(" ", " L ") +
                " L " +
                right +
                " " +
                bottom +
                " L " +
                left +
                " " +
                bottom +
                " Z"
              }
              fill="url(#chart-fill)"
            />
            <polyline
              points={line}
              fill="none"
              stroke={color}
              strokeWidth="2"
              vectorEffect="non-scaling-stroke"
            />
          </>
        )}
        {last != null && (
          <>
            <line
              x1={left}
              y1={y(last)}
              x2={right}
              y2={y(last)}
              stroke={color}
              strokeOpacity=".45"
              strokeDasharray="4 5"
            />
            <circle
              cx={x(values.length - 1)}
              cy={y(last)}
              r="3.5"
              fill={color}
            />
            <rect
              x={right + 6}
              y={y(last) - 11}
              width="68"
              height="22"
              rx="3"
              fill={color}
            />
            <text
              x={right + 40}
              y={y(last) + 4}
              textAnchor="middle"
              className="price-label"
            >
              {last.toFixed(2)}
            </text>
          </>
        )}
        {active && (
          <>
            <line
              x1={x(hover)}
              x2={x(hover)}
              y1={top}
              y2={bottom}
              className="crosshair"
            />
            <line
              x1={left}
              x2={right}
              y1={y(Number(active.price))}
              y2={y(Number(active.price))}
              className="crosshair"
            />
            <circle
              cx={x(hover)}
              cy={y(Number(active.price))}
              r="5"
              stroke="#101318"
              strokeWidth="2"
              fill={color}
            />
          </>
        )}
      </svg>
      {points.length < 2 && (
        <div className="chart-wait">
          <span className="pulse-dot" />
          Waiting for the next quote
          <span>Only observed prices appear here.</span>
        </div>
      )}
    </div>
  );
}
