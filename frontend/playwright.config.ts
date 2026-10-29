import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests (E16-S07, 08-frontend §13) against a running stack: the production compose stack (proxy on
 * http://localhost:8080, the default) or `./run-dev.sh` + `npm run dev` (E2E_BASE_URL=http://localhost:5173).
 * They log in as the bootstrap admin (E2E_ADMIN_USERNAME / E2E_ADMIN_PASSWORD) to prepare their own data.
 * E2E_CHROMIUM points at an installed Chromium instead of Playwright's own (`npx playwright install chromium`).
 */
export default defineConfig({
  testDir: "./e2e",
  testMatch: "**/*.e2e.ts",                 // not *.test.ts: those are Vitest's
  timeout: 60_000,
  retries: process.env.CI ? 1 : 0,
  reporter: [["list"]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:8080",
    trace: "retain-on-failure",
    launchOptions: process.env.E2E_CHROMIUM ? { executablePath: process.env.E2E_CHROMIUM } : {},
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
