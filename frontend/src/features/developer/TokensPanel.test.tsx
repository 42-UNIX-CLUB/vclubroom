import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { TokensPanel } from "./TokensPanel";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn(), DELETE: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const TOKEN = "vcr_pat_k3yVa1ue0nlyOnce";
const backup = { id: "t-1", name: "Backup script", scopes: ["profile:read", "forum:read", "forum:write"], lastFour: "a1b2",
  createdAt: "2026-10-01T10:00:00Z", expiresAt: "2026-12-01T10:00:00Z", lastUsedAt: "2026-10-29T18:30:00Z", expired: false };
const bot = { id: "t-2", name: "Discord bot", scopes: ["events:read"], lastFour: "c3d4", createdAt: "2026-10-02T10:00:00Z", expired: false };
const old = { id: "t-3", name: "Old", scopes: ["chat:read"], lastFour: "e5f6", createdAt: "2026-09-01T10:00:00Z",
  expiresAt: "2026-10-01T10:00:00Z", expired: true };

function renderPanel(tokens: object[]) {
  vi.mocked(api.GET).mockResolvedValue({ data: tokens } as never);
  vi.mocked(api.POST).mockImplementation(((_path: string, { body }: { body: { name: string; scopes: string[] } }) =>
    Promise.resolve({ data: { token: TOKEN, details: { id: "t-new", name: body.name, scopes: body.scopes, lastFour: "Once", expired: false } } })) as never);
  vi.mocked(api.DELETE).mockResolvedValue({} as never);
  render(<QueryClientProvider client={new QueryClient()}><TokensPanel /></QueryClientProvider>);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("TokensPanel", () => {
  it("lists each token's scopes, expiry and last use, without its value", async () => {
    renderPanel([backup, bot, old]);

    await screen.findByText("Backup script", {}, { timeout: 3000 });
    const list = screen.getByRole("list", { name: "Your tokens" });
    const [first, second, third] = within(list).getAllByRole("listitem");
    expect(first.textContent).toContain("vcr_pat_…a1b2");
    expect(first.textContent).toContain("Profile: read · Forum: read, write");
    expect(first.textContent).toContain("Expires 1 Dec 2026");
    expect(first.textContent).toContain("Last used 29 Oct 2026");
    expect(within(second).getByText("Never expires")).toBeTruthy();
    expect(second.textContent).toContain("Never used");
    expect(within(third).getByText("Expired")).toBeTruthy();
  });

  it("creates a token, warns about one that never expires, and shows its value once", async () => {
    renderPanel([]);
    fireEvent.click(await screen.findByRole("button", { name: /New token/ }, { timeout: 3000 }));
    const dialog = await screen.findByRole("dialog");
    const create = within(dialog).getByRole("button", { name: "Create token" }) as HTMLButtonElement;
    expect(create.disabled).toBe(true);

    fireEvent.change(within(dialog).getByLabelText("Name"), { target: { value: "  Discord bot " } });
    fireEvent.click(within(dialog).getByLabelText("Events: write"));                 // ticks read too
    expect((within(dialog).getByLabelText("Events: read") as HTMLInputElement).checked).toBe(true);
    fireEvent.click(within(dialog).getByLabelText("Never"));
    expect(within(dialog).getByRole("note").textContent).toContain("works until you revoke it");
    fireEvent.click(create);

    await waitFor(() => expect(api.POST).toHaveBeenCalledWith("/api/me/tokens",
      { body: { name: "Discord bot", scopes: ["events:write", "events:read"], expiresAt: undefined } }));
    expect(((await screen.findByLabelText("New token")) as HTMLInputElement).value).toBe(TOKEN);
    expect(screen.getByText(/Copy it now: it won't be shown again/)).toBeTruthy();
  });

  it("stops at five active tokens; expired ones don't count", async () => {
    const five = [1, 2, 3, 4, 5].map((n) => ({ ...bot, id: `t-${n}`, name: `Token ${n}` }));
    renderPanel([...five, old]);

    await screen.findByText("Token 5", {}, { timeout: 3000 });
    expect((screen.getByRole("button", { name: /New token/ }) as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByText(/You have 5 active tokens/)).toBeTruthy();
  });

  it("revokes a token after confirming", async () => {
    renderPanel([backup]);
    fireEvent.click(await screen.findByRole("button", { name: "Revoke" }, { timeout: 3000 }));
    fireEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Revoke" }));

    await waitFor(() => expect(api.DELETE).toHaveBeenCalledWith("/api/me/tokens/{tokenId}", { params: { path: { tokenId: "t-1" } } }));
  });
});
