export default function Icon({ name, size = 18 }) {
  const paths = {
    grid: "M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z",
    chart: "M3 20V4 M3 20h18 M6 14l4-5 4 3 6-8",
    search: "M21 21l-5-5 M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0",
    arrow: "M5 16L17 4 M5 4h12v12",
    wallet: "M3 7V5a2 2 0 0 1 2-2h14v4 M3 7h18v14H3z M16 12h5v5h-5z",
    chevron: "M8 4l8 8-8 8",
    close: "M5 5l14 14 M19 5L5 19",
    activity: "M2 12h4l4-8 4 16 4-8h4",
    clock: "M12 8v5l3 2 M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0",
  };
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name] || paths.chart} />
    </svg>
  );
}
