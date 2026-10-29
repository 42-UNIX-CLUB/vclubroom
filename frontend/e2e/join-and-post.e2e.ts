import { type APIRequestContext, expect, request, test } from "@playwright/test";

/**
 * The key flow (E16-S07): a student logs in with a local account, joins an OPEN club, posts in its forum and finds
 * the post on the club's home page. The data is made through the API first, as the bootstrap admin, with a unique
 * suffix, so the test can run again and again on the same database.
 */

const ADMIN = { username: process.env.E2E_ADMIN_USERNAME ?? "admin", password: process.env.E2E_ADMIN_PASSWORD ?? "change-me-admin" };
const PASSWORD = "e2e-temporary-pass";
const suffix = Date.now().toString(36);
const student = { username: `e2e-student-${suffix}`, name: `E2E Student ${suffix}` };
let clubSlug = "";

/** An API session like the SPA's: the XSRF cookie from /api/auth/csrf, sent back in X-XSRF-TOKEN. */
async function sessionAs(baseURL: string, username: string, password: string) {
  const api = await request.newContext({ baseURL });
  await api.get("/api/auth/csrf");
  const xsrf = async () => ({ "X-XSRF-TOKEN": (await api.storageState()).cookies.find((c) => c.name === "XSRF-TOKEN")?.value ?? "" });
  const login = await api.post("/api/auth/login", { data: { username, password }, headers: await xsrf() });
  expect(login.ok(), `log in as ${username}`).toBeTruthy();
  const send = async (method: "POST" | "PATCH", path: string, data: unknown) => {
    const response = await api.fetch(path, { method, data, headers: await xsrf() });
    expect(response.ok(), `${method} ${path}: ${response.status()}`).toBeTruthy();
    return response.json();
  };
  return { api, send };
}

async function waitForSpaces(api: APIRequestContext, clubId: string) {
  await expect.poll(async () => ((await (await api.get(`/api/clubs/${clubId}/spaces`)).json()) as unknown[]).length,
    { message: "the club's default space", timeout: 15_000 }).toBeGreaterThan(0);
}

test.beforeAll(async ({ baseURL }) => {
  const admin = await sessionAs(baseURL!, ADMIN.username, ADMIN.password);
  const owner = { username: `e2e-owner-${suffix}`, name: `E2E Owner ${suffix}` };
  for (const person of [owner, student]) {
    await admin.send("POST", "/api/admin/users", { username: person.username, displayName: person.name, temporaryPassword: PASSWORD });
  }
  const founder = await sessionAs(baseURL!, owner.username, PASSWORD);
  const club = await founder.send("POST", "/api/clubs", { name: `E2E Club ${suffix}`, category: "Tech" });
  clubSlug = club.slug;
  await admin.send("POST", `/api/admin/clubs/${club.id}/approve`, {});
  await waitForSpaces(founder.api, club.id);
  await founder.send("PATCH", `/api/clubs/${club.id}`, { joinPolicy: "OPEN" });
});

test("a student joins an open club, posts, and sees the post on the club page", async ({ page }) => {
  const text = `Hello from the e2e test ${suffix}`;

  await page.goto("/login");
  await page.locator("#username").fill(student.username);
  await page.locator("#password").fill(PASSWORD);
  await page.getByRole("button", { name: "Log in with password" }).click();
  await expect(page).toHaveURL(/\/$/);

  await page.goto(`/c/${clubSlug}`);
  await page.getByRole("button", { name: "Join club" }).click();
  await expect(page.getByRole("button", { name: /Member/ })).toBeVisible();

  await page.getByRole("navigation", { name: "Spaces" }).getByRole("link", { name: "Posts" }).click();
  await page.getByRole("textbox", { name: "Write a post" }).fill(text);
  await page.getByRole("button", { name: "Post", exact: true }).click();
  await expect(page.getByText(text)).toBeVisible();

  await page.goto(`/c/${clubSlug}`);
  await expect(page.getByRole("region", { name: "Latest posts" }).getByText(text)).toBeVisible();
});
