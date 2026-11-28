import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { AuthorizedAppsPanel } from "./AuthorizedAppsPanel";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), DELETE: vi.fn() } }));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function renderPanel(apps: object[]) {
  vi.mocked(api.GET).mockResolvedValue({ data: apps } as never);
  vi.mocked(api.DELETE).mockResolvedValue({} as never);
  render(<QueryClientProvider client={new QueryClient()}><AuthorizedAppsPanel /></QueryClientProvider>);
}

describe("AuthorizedAppsPanel", () => {
  it("lists the apps let in with what they may do, and removes one's access after confirming", async () => {
    renderPanel([{ appId: "a-1", name: "Club stats", homepageUrl: "https://stats.example", scopes: ["profile:read", "forum:read", "forum:write"] }]);

    const item = await screen.findByRole("listitem", {}, { timeout: 3000 });
    expect(within(item).getByRole("link", { name: "Club stats" })).toBeTruthy();
    expect(item.textContent).toContain("Profile: read · Forum: read, write");

    fireEvent.click(within(item).getByRole("button", { name: "Remove access" }));
    fireEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Remove access" }));
    await waitFor(() => expect(api.DELETE).toHaveBeenCalledWith("/api/me/authorized-apps/{appId}", { params: { path: { appId: "a-1" } } }));
  });

  it("says when no app was let in", async () => {
    renderPanel([]);

    expect(await screen.findByText("You haven't let any app in.", {}, { timeout: 3000 })).toBeTruthy();
  });
});
