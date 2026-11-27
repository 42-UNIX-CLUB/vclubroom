import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { AppsPanel } from "./AppsPanel";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn(), PATCH: vi.fn(), DELETE: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const stats = { id: "a-1", clientId: "vcr_app_stats", name: "Club stats", description: "Charts of my clubs", confidential: false,
  homepageUrl: "https://stats.example", redirectUris: ["https://stats.example/callback"], createdAt: "2026-10-01T10:00:00Z" };
const bot = { id: "a-2", clientId: "vcr_app_bot", name: "Bot", confidential: true, redirectUris: ["https://bot.example/oauth"],
  createdAt: "2026-10-02T10:00:00Z" };

function renderPanel(apps: object[]) {
  vi.mocked(api.GET).mockResolvedValue({ data: apps } as never);
  vi.mocked(api.POST).mockImplementation(((path: string, init: { body?: { name: string; confidential: boolean } }) =>
    Promise.resolve({ data: path === "/api/me/apps"
      ? { app: { ...bot, id: "a-new", name: init.body?.name, confidential: init.body?.confidential }, clientSecret: init.body?.confidential ? "vcr_secret_new" : undefined }
      : { app: bot, clientSecret: "vcr_secret_renewed" } })) as never);
  vi.mocked(api.PATCH).mockResolvedValue({ data: stats } as never);
  vi.mocked(api.DELETE).mockResolvedValue({} as never);
  render(<QueryClientProvider client={new QueryClient()}><AppsPanel /></QueryClientProvider>);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("AppsPanel", () => {
  it("lists each app with its kind, client ID and redirect URIs", async () => {
    renderPanel([stats, bot]);

    await screen.findByText("Club stats", {}, { timeout: 3000 });
    const [first, second] = within(screen.getByRole("list", { name: "Your apps" })).getAllByRole("listitem");
    expect(within(first).getByText("Public")).toBeTruthy();
    expect(first.textContent).toContain("vcr_app_stats");
    expect(first.textContent).toContain("Redirects to https://stats.example/callback");
    expect(within(first).queryByRole("button", { name: "New secret" })).toBeNull();      // public apps have none
    expect(within(second).getByText("Confidential")).toBeTruthy();
  });

  it("registers a confidential app and shows its secret once", async () => {
    renderPanel([]);
    fireEvent.click(await screen.findByRole("button", { name: /Register an app/ }, { timeout: 3000 }));
    const dialog = await screen.findByRole("dialog");
    fireEvent.change(within(dialog).getByLabelText("Name"), { target: { value: "Discord bot" } });
    fireEvent.change(within(dialog).getByLabelText("Redirect URIs"), { target: { value: "https://bot.example/a\n\n https://bot.example/b " } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Register" }));

    await waitFor(() => expect(api.POST).toHaveBeenCalledWith("/api/me/apps", { body: {
      name: "Discord bot", description: "", homepageUrl: "", redirectUris: ["https://bot.example/a", "https://bot.example/b"], confidential: true } }));
    expect(((await screen.findByLabelText("Client secret")) as HTMLInputElement).value).toBe("vcr_secret_new");
  });

  it("edits an app, renews a confidential app's secret and deletes an app after confirming", async () => {
    renderPanel([stats, bot]);
    await screen.findByText("Club stats", {}, { timeout: 3000 });
    const [first, second] = within(screen.getByRole("list", { name: "Your apps" })).getAllByRole("listitem");

    fireEvent.click(within(first).getByRole("button", { name: "Edit" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).queryByText("Kind")).toBeNull();                              // chosen once
    fireEvent.change(within(dialog).getByLabelText("Name"), { target: { value: "Club charts" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Save" }));
    await waitFor(() => expect(api.PATCH).toHaveBeenCalledWith("/api/me/apps/{appId}", expect.objectContaining({
      params: { path: { appId: "a-1" } }, body: expect.objectContaining({ name: "Club charts" }) })));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());

    fireEvent.click(within(second).getByRole("button", { name: "New secret" }));
    fireEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "New secret" }));
    expect(((await screen.findByLabelText("Client secret")) as HTMLInputElement).value).toBe("vcr_secret_renewed");
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());

    fireEvent.click(within(first).getByRole("button", { name: "Delete" }));
    fireEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Delete" }));
    await waitFor(() => expect(api.DELETE).toHaveBeenCalledWith("/api/me/apps/{appId}", { params: { path: { appId: "a-1" } } }));
  });

  it("stops at three apps", async () => {
    renderPanel([stats, bot, { ...bot, id: "a-3", name: "Third" }]);

    await screen.findByText("Third", {}, { timeout: 3000 });
    expect((screen.getByRole("button", { name: /Register an app/ }) as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByText(/You have 3 apps/)).toBeTruthy();
  });
});
