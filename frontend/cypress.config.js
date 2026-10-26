import { defineConfig } from "cypress";

// Runs against the packaged app, the same target as the Playwright suite.
export default defineConfig({
  e2e: {
    baseUrl: process.env.E2E_BASE_URL || "http://127.0.0.1:8080",
    specPattern: "cypress/e2e/**/*.cy.js",
    supportFile: "cypress/support/e2e.js",
    video: false,
    defaultCommandTimeout: 8000,
  },
});
