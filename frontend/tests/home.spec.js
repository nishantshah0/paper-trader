import { test, expect } from "@playwright/test";

test("home page introduces the product and launches the working terminal", async ({
  page,
  request,
}) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: /Get a feel/ })).toBeVisible();
  await page
    .getByRole("link", { name: "Start practicing", exact: true })
    .click();
  await expect(page).toHaveURL(/\/terminal$/);
  await expect(
    page.getByRole("heading", { name: "Trading terminal", exact: true }),
  ).toBeVisible();
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "Trading terminal", exact: true }),
  ).toBeVisible();
  await page
    .getByRole("link", { name: "Paper Trader home", exact: true })
    .click();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole("heading", { name: /Get a feel/ })).toBeVisible();
  const trailingSlash = await request.get("/terminal/");
  expect(trailingSlash.status()).toBe(200);
  const missingApi = await request.get("/api/does-not-exist");
  expect(missingApi.status()).toBe(404);
});

test("instrument previews open the corresponding chart", async ({ page }) => {
  await page.goto("/");
  await page.locator('.home-quote[href="/terminal?symbol=NVDA"]').click();
  await expect(
    page.getByRole("heading", { name: "NVDA NVIDIA", exact: true }),
  ).toBeVisible();
  await expect(page.getByLabel("Instrument", { exact: true })).toHaveValue(
    "NVDA",
  );
});

test("home sections, FAQ, and mobile navigation are usable", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  const menu = page.getByRole("button", { name: "Toggle navigation" });
  await menu.click();
  await expect(menu).toHaveAttribute("aria-expanded", "true");
  await page
    .getByRole("navigation", { name: "Main navigation" })
    .getByRole("link", { name: "FAQ", exact: true })
    .click();
  await expect(menu).toHaveAttribute("aria-expanded", "false");
  await page.getByText("Am I trading with real money?").click();
  await expect(
    page.getByText(/No\. Every account starts with virtual cash/),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page
    .getByRole("link", { name: "Launch your terminal", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Trading terminal", exact: true }),
  ).toBeVisible();
});

test("home layout renders at desktop and mobile widths", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto("/");
  await expect(page.locator(".home-quote").first()).toContainText("$");
  await expect(page.locator(".terminal-preview img")).toHaveJSProperty(
    "complete",
    true,
  );
  await page.screenshot({
    path: "test-results/home-desktop.png",
    fullPage: false,
  });
  await page.screenshot({ path: "test-results/home-full.png", fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: "test-results/home-mobile.png",
    fullPage: true,
  });
  await page.screenshot({
    path: "test-results/home-mobile-hero.png",
    fullPage: false,
  });
});
