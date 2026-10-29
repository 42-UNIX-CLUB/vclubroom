#!/usr/bin/env node
// README screenshots and the "join in 3 clicks" GIF (E16-S05), taken from a running site.
//
//   chromium --headless=new --remote-debugging-port=9222 --user-data-dir="$(mktemp -d)" &
//   BASE=https://42clubroom.ensui.dev CLUB=<slug> ADMIN_PASSWORD=<password> \
//   JOINER=<login> JOINER_PASSWORD=<password> JOIN_CLUB=<slug of a club JOINER is not in> node scripts/screenshots.mjs
//
// Writes docs/media/{landing,club,posts,calendar,history,admin}.png, and docs/media/join-flow.gif when JOINER is set
// (needs ffmpeg). Run it from the repository root. Uses Node's built-in WebSocket (Node 22+): no dependencies.
import { execFileSync } from "node:child_process";
import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const env = (name, fallback) => process.env[name] ?? fallback ?? (() => { throw new Error(`set ${name}`); })();
const BASE = env("BASE", "http://localhost:5173");
const CLUB = env("CLUB");
const ADMIN = env("ADMIN", "admin");
const ADMIN_PASSWORD = env("ADMIN_PASSWORD");
const OUT = "docs/media";
mkdirSync(OUT, { recursive: true });

// --- a minimal DevTools-protocol driver ---
const target = (await (await fetch(`http://127.0.0.1:${env("CDP_PORT", "9222")}/json/list`)).json()).find((t) => t.type === "page");
const ws = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((ok) => ws.addEventListener("open", ok, { once: true }));
let seq = 0;
const pending = new Map();
ws.addEventListener("message", (m) => { const d = JSON.parse(m.data); pending.get(d.id)?.(d); pending.delete(d.id); });
const send = (method, params = {}) => new Promise((ok) => { const id = ++seq; pending.set(id, ok); ws.send(JSON.stringify({ id, method, params })); });
const ev = async (expression) => (await send("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true })).result?.result?.value;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function waitFor(expression, label, timeout = 15000) {
  for (const end = Date.now() + timeout; Date.now() < end; await sleep(200)) if (await ev(expression).catch(() => false)) return;
  throw new Error(`timed out waiting for ${label}`);
}
const has = (text) => `document.body.innerText.includes(${JSON.stringify(text)})`;
async function go(path) {
  await send("Page.navigate", { url: BASE + path });
  await sleep(500);
  await waitFor("document.readyState === 'complete'", `${path} to load`);
  await sleep(1200);                                       // data, images and fonts settle
}
async function click(expression, label) {
  await waitFor(`!!(${expression})`, label);
  const p = await ev(`(() => { const e = ${expression}; e.scrollIntoView({ block: "center" }); const r = e.getBoundingClientRect();
    return { x: r.x + r.width / 2, y: r.y + r.height / 2 }; })()`);
  for (const type of ["mouseMoved", "mousePressed", "mouseReleased"]) {
    await send("Input.dispatchMouseEvent", { type, x: p.x, y: p.y, button: "left", clickCount: 1 });
  }
}
async function typeInto(selector, text) {
  await click(`document.querySelector(${JSON.stringify(selector)})`, selector);
  await send("Input.insertText", { text });
}
async function shot(file) {
  await ev("window.scrollTo(0, 0)");
  const { result } = await send("Page.captureScreenshot", { format: "png" });
  writeFileSync(file, Buffer.from(result.data, "base64"));
  console.log(`wrote ${file}`);
}
async function login(user, password) {
  await go("/login");
  await typeInto("#username", user);
  await typeInto("#password", password);
  await click(`[...document.querySelectorAll("button")].find((b) => b.textContent.trim() === "Log in with password")`, "log in");
  await waitFor(`location.pathname !== "/login"`, `${user} logged in`);
}
async function logout() {
  await send("Network.clearBrowserCookies");
}

await send("Page.enable");
await send("Network.enable");
await send("Emulation.setDeviceMetricsOverride", { width: 1280, height: 800, deviceScaleFactor: 1, mobile: false });
await logout();

// --- the screenshots ---
await go("/");
await shot(`${OUT}/landing.png`);

await login(ADMIN, ADMIN_PASSWORD);
const club = await ev(`fetch("/api/clubs/${CLUB}").then((r) => r.json())`);
const spaces = await ev(`fetch("/api/clubs/${club.id}/spaces").then((r) => r.json())`);
const space = spaces.find((s) => s.channels.some((c) => c.type === "FORUM"));
const forum = space.channels.find((c) => c.type === "FORUM");

await go(`/c/${CLUB}`);
await shot(`${OUT}/club.png`);
await go(`/c/${CLUB}/s/${space.slug}/${forum.slug}`);
await shot(`${OUT}/posts.png`);
await go("/calendar");
await waitFor(`!!document.querySelector(".fc")`, "the calendar");
await shot(`${OUT}/calendar.png`);
await go(`/c/${CLUB}/history`);
await shot(`${OUT}/history.png`);
await go("/admin/clubs");
await shot(`${OUT}/admin.png`);

// --- the join GIF: directory → club → Join, one frame per step ---
if (process.env.JOINER) {
  const joinClub = env("JOIN_CLUB");
  const frames = mkdtempSync(join(tmpdir(), "join-flow-"));
  let n = 0;
  const frame = async (times = 1) => { for (let i = 0; i < times; i++) await shot(join(frames, `frame-${String(++n).padStart(2, "0")}.png`)); };
  await logout();
  await login(env("JOINER"), env("JOINER_PASSWORD"));
  await go("/clubs");
  await frame(2);
  await click(`document.querySelector('a[href="/clubs/${joinClub}/about"]')`, "the club's card");
  await waitFor(`location.pathname === "/clubs/${joinClub}/about"`, "the public profile");
  await sleep(1200);
  await frame(2);
  await click(`[...document.querySelectorAll("a, button")].find((e) => /^(Join|Request to join)/.test(e.textContent.trim()))`, "Join");
  await waitFor(`${has("Welcome to")} || ${has("Request sent")}`, "the join to finish");
  await frame(3);
  execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-framerate", "1", "-i", join(frames, "frame-%02d.png"),
    "-vf", "scale=960:-1:flags=lanczos,split[a][b];[a]palettegen[p];[b][p]paletteuse", `${OUT}/join-flow.gif`]);
  console.log(`wrote ${OUT}/join-flow.gif`);
}
ws.close();
