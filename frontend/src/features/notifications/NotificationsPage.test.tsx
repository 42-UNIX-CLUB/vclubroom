import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { NotificationsPage } from "./NotificationsPage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn() } }));

const unread = { id: "n1", type: "NEW_COMMENT", title: "New comment on your post", body: "See you there", link: "/posts/p1", read: false, createdAt: new Date().toISOString() };
const read = { id: "n2", type: "CLUB_APPROVED", title: "Chess Club is approved", link: "/c/chess", read: true, createdAt: new Date().toISOString() };

function renderPage() {
  vi.mocked(api.GET).mockImplementation(((path: string, init?: { params: { query: { unreadOnly?: boolean } } }) => Promise.resolve({
    data: path === "/api/notifications/unread-count" ? { count: 1 }
      : { items: init?.params.query.unreadOnly ? [unread] : [unread, read], nextCursor: null },
  })) as never);
  vi.mocked(api.POST).mockResolvedValue({} as never);
  render(<QueryClientProvider client={new QueryClient()}><MemoryRouter><NotificationsPage /></MemoryRouter></QueryClientProvider>);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("NotificationsPage", () => {
  it("lists everything, or the unread only, and marks all as read", async () => {
    renderPage();

    expect(await screen.findByText("Chess Club is approved")).toBeTruthy();
    expect(screen.getByText("Unread:")).toBeTruthy();                                   // screen readers hear which
    fireEvent.click(screen.getByRole("tab", { name: "Unread (1)" }));
    await waitFor(() => expect(screen.queryByText("Chess Club is approved")).toBeNull());
    fireEvent.click(screen.getByRole("button", { name: "Mark all as read" }));
    await waitFor(() => expect(api.POST).toHaveBeenCalledWith("/api/notifications/read-all"));
  });
});
