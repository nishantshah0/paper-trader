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
  await page.goto("/");
  await expect(page.getByText("Account #" + id, { exact: true })).toBeVisible();
  await expect(page.getByText("Connected", { exact: true })).toBeVisible();
}

test("create an account, buy and sell, and show trade history", async ({
  page,
}) => {
  await page.goto("/");
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
