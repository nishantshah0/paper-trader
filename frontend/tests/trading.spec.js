import { test, expect } from "@playwright/test";

async function account(request) {
  const response = await request.post("/api/accounts", {
    data: {
      username:
        "e2e_" + Date.now() + "_" + Math.random().toString(36).slice(2, 6),
    },
  });
  expect(response.status()).toBe(201);
  return response.json();
}
async function open(page, id) {
  await page.addInitScript(
    (id) => localStorage.setItem("paper-trader.account", String(id)),
    id,
  );
  await page.goto("/terminal");
  await expect(page.getByText("Account #" + id, { exact: true })).toBeVisible();
  await expect(page.getByText("Connected", { exact: true })).toBeVisible();
}

test("create an account, buy and sell, and show trade history", async ({
  page,
}) => {
  await page.goto("/terminal");
  await page.getByLabel("New account name").fill("ui_" + Date.now());
  await page
    .getByRole("button", { name: "Create account", exact: true })
    .click();
  await expect(page.getByText(/Account #/)).toBeVisible();
  await page.getByRole("button", { name: "Buy AAPL", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("filled");
  const positions = page.locator("section.positions");
  await expect(
    positions.getByRole("button", { name: "AAPL", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Sell", exact: true }).click();
  await page.getByRole("button", { name: "Sell AAPL", exact: true }).click();
  await expect(
    positions.getByText("Your portfolio starts with a first trade."),
  ).toBeVisible();
  await page.getByRole("tab", { name: /Trade history/ }).click();
  await expect(page.locator("section.history tbody tr")).toHaveCount(2);
});

test("resting limit order can be cancelled", async ({ page, request }) => {
  const a = await account(request);
  await open(page, a.id);
  await page.getByLabel("Order type").selectOption("LIMIT");
  await page.getByLabel("Limit price").fill("1");
  await page.getByRole("button", { name: "Buy AAPL", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("open");
  await page.getByRole("button", { name: /Cancel #/ }).click();
  await expect(page.getByRole("status")).toContainText("cancelled");
  await expect(page.getByRole("button", { name: /Cancel #/ })).toHaveCount(0);
});

test("a committed fill reaches both tabs over WebSocket", async ({
  context,
  page,
  request,
}) => {
  const a = await account(request);
  await open(page, a.id);
  const second = await context.newPage();
  await open(second, a.id);
  // Poll fallback is 15 seconds. Both assertions must pass inside 5 seconds.
  const response = await request.post("/api/accounts/" + a.id + "/orders", {
    data: { symbol: "MSFT", side: "BUY", quantity: 2 },
  });
  expect(response.status()).toBe(201);
  await expect(
    page
      .locator("section.positions")
      .getByRole("button", { name: "MSFT", exact: true }),
  ).toBeVisible({ timeout: 5000 });
  await expect(
    second
      .locator("section.positions")
      .getByRole("button", { name: "MSFT", exact: true }),
  ).toBeVisible({ timeout: 5000 });
});

test("retries are idempotent and errors are visible", async ({
  page,
  request,
}) => {
  const a = await account(request),
    data = { symbol: "AAPL", side: "BUY", quantity: 1 },
    headers = { "Idempotency-Key": "e2e-retry" };
  const first = await request.post("/api/accounts/" + a.id + "/orders", {
    data,
    headers,
  });
  const second = await request.post("/api/accounts/" + a.id + "/orders", {
    data,
    headers,
  });
  expect((await first.json()).id).toBe((await second.json()).id);
  const trades = await request.get("/api/accounts/" + a.id + "/trades");
  expect((await trades.json()).length).toBe(1);
  await open(page, a.id);
  await page.getByLabel("Quantity").fill("1000000");
  await page.getByRole("button", { name: "Buy AAPL", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("insufficient cash");
});

test("dashboard fits desktop and mobile viewports", async ({
  page,
  request,
}) => {
  const a = await account(request);
  await request.post("/api/accounts/" + a.id + "/orders", {
    data: { symbol: "AAPL", side: "BUY", quantity: 12 },
  });
  await request.post("/api/accounts/" + a.id + "/orders", {
    data: { symbol: "NVDA", side: "BUY", quantity: 20 },
  });
  await page.setViewportSize({ width: 1440, height: 1100 });
  await open(page, a.id);
  await expect(page.locator("section.positions tbody tr")).toHaveCount(2);
  await expect(
    page.getByRole("img", { name: "Price movement during this session" }),
  ).toBeVisible({ timeout: 20000 });
  await page.screenshot({
    path: "test-results/dashboard-desktop.png",
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(
    page.getByRole("button", { name: "Buy AAPL", exact: true }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: "test-results/dashboard-mobile.png",
    fullPage: true,
  });
});

test("terminal search, symbol selection, chart range and order shortcuts work", async ({
  page,
  request,
}) => {
  const a = await account(request);
  await open(page, a.id);
  await page.getByRole("textbox", { name: "Search symbols" }).fill("nvidia");
  await expect(page.locator(".quote-row")).toHaveCount(1);
  await page.locator(".quote-row").click();
  await expect(
    page.getByRole("heading", { name: "NVDA NVIDIA" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "10 shares", exact: true }).click();
  await expect(
    page.getByRole("spinbutton", { name: "Quantity", exact: true }),
  ).toHaveValue("10");
  await page.getByRole("button", { name: "5m", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "5m", exact: true }),
  ).toHaveAttribute("aria-pressed", "true");
  await page.getByLabel("Order type").selectOption("LIMIT");
  await page.getByLabel("Limit price").fill("1");
  await page.getByRole("button", { name: "Buy NVDA", exact: true }).click();
  await expect(page.getByRole("tab", { name: /Open orders/ })).toHaveAttribute(
    "aria-selected",
    "true",
  );
  await page.getByRole("button", { name: /Cancel #/ }).click();
  await page.getByRole("tab", { name: /All orders/ }).click();
  await expect(page.getByRole("tabpanel")).toContainText("CANCELLED");
});

test("chart keeps server-observed history across a browser reload", async ({
  page,
  request,
}) => {
  await expect
    .poll(
      async () => {
        const r = await request.get("/api/quotes/history");
        return (await r.json()).AAPL.length;
      },
      { timeout: 20000 },
    )
    .toBeGreaterThan(1);
  const a = await account(request);
  await open(page, a.id);
  await expect(page.locator(".chart-readout-end")).not.toHaveText(
    "0 OBSERVED QUOTES",
  );
  await page.reload();
  await expect(page.locator(".chart polyline")).toBeVisible();
  await expect(page.locator(".chart-wait")).toHaveCount(0);
});

test("account switching and keyboard activity tabs work", async ({
  page,
  request,
}) => {
  const first = await account(request),
    second = await account(request);
  await open(page, first.id);
  await page.locator(".account-toggle").click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByLabel("Existing account ID").fill(String(second.id));
  await page.getByRole("button", { name: "Open account", exact: true }).click();
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await expect(
    page.getByText("Account #" + second.id, { exact: true }),
  ).toBeVisible();
  const positions = page.getByRole("tab", { name: /Positions/ });
  await positions.focus();
  await page.keyboard.press("ArrowRight");
  await expect(page.getByRole("tab", { name: /Open orders/ })).toHaveAttribute(
    "aria-selected",
    "true",
  );
  await page.keyboard.press("End");
  await expect(page.getByRole("tab", { name: /All orders/ })).toHaveAttribute(
    "aria-selected",
    "true",
  );
});
