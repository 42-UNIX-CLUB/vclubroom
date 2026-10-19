import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { NotificationBell } from "./NotificationBell";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn() } }));
const live = vi.hoisted(() => ({ handlers: new Map<string, () => void>() }));
vi.mock("@/shared/realtime/RealtimeProvider", () => ({
  useTopic: (destination: string | null, handler: () => void) => { if (destination) live.handlers.set(destination, handler); },
}));

const items = [
  { id: "n1", type: "NEW_POST", title: "Friday blitz", body: "Bring a clock", link: "/posts/p1", read: false, createdAt: new Date().toISOString() },
  { id: "n2", type: "CLUB_APPROVED", title: "Chess Club is approved", link: "/c/chess", read: true, createdAt: new Date().toISOString() },
];

function backend(unread: number[]) {
  const counts = [...unread];
  vi.mocked(api.GET).mockImplementation(((path: string) => Promise.resolve({
    data: path === "/api/notifications/unread-count" ? { count: counts.length > 1 ? counts.shift() : counts[0] } : { items, nextCursor: null },
  })) as never);
  vi.mocked(api.POST).mockResolvedValue({} as never);
}

function renderBell() {
  const router = createMemoryRouter([
    { path: "/", element: <NotificationBell /> },
    { path: "/posts/:postId", element: <p>post page</p> },
  ], { initialEntries: ["/"] });
  render(<QueryClientProvider client={new QueryClient()}><RouterProvider router={router} /></QueryClientProvider>);
  return router;
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("NotificationBell", () => {
  it("shows the unread count, live", async () => {
    backend([1, 2]);
    renderBell();

    expect(await screen.findByRole("button", { name: "Notifications, 1 unread" })).toBeTruthy();
    act(() => live.handlers.get("/user/queue/notifications")?.());                 // a push: ask again
    expect(await screen.findByRole("button", { name: "Notifications, 2 unread" })).toBeTruthy();
  });

  it("opens a notification: marks it read and goes to its target", async () => {
    backend([1]);
    const router = renderBell();

    fireEvent.click(await screen.findByRole("button", { name: "Notifications, 1 unread" }));
    fireEvent.click(await screen.findByRole("menuitem", { name: /Friday blitz/ }));

    await waitFor(() => expect(router.state.location.pathname).toBe("/posts/p1"));
    expect(api.POST).toHaveBeenCalledWith("/api/notifications/{notificationId}/read", { params: { path: { notificationId: "n1" } } });
  });
});
