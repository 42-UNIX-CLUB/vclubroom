import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { createMemoryRouter, Outlet, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { ClubEventsPage } from "./ClubEventsPage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));
vi.mock("@fullcalendar/react", () => import("./fullCalendarStub"));

const items = [
  { id: "e1", title: "Blitz night", start: "2026-11-03T18:00:00Z", end: "2026-11-03T21:00:00Z", clubName: "Chess Club", status: "SCHEDULED" },
  { id: "e2", title: "Simul", start: "2026-11-05T18:00:00Z", end: "2026-11-05T20:00:00Z", clubName: "Chess Club", status: "CANCELLED" },
];

function renderPage(status: string, permissions: string[]) {
  vi.mocked(api.GET).mockImplementation(((path: string) => Promise.resolve({
    data: path === "/api/clubs/{clubId}/membership/me" ? { member: true, owner: false, roles: [], permissions }
      : path === "/api/clubs/{clubId}/events" ? items : [],
  })) as never);
  const router = createMemoryRouter([
    { element: <Outlet context={{ id: "c1", status }} />, children: [{ path: "/c/chess/events", element: <ClubEventsPage /> }] },
    { path: "/events/:eventId", element: <p>event page</p> },
  ], { initialEntries: ["/c/chess/events"] });
  render(<QueryClientProvider client={new QueryClient()}><RouterProvider router={router} /></QueryClientProvider>);
  return router;
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("ClubEventsPage", () => {
  it("shows the club's events of the visible range, cancelled ones struck through", async () => {
    const router = renderPage("ACTIVE", []);

    // The calendar view loads lazily (LazyCalendarView): give a cold test worker more than the default second.
    expect(await screen.findByRole("button", { name: "Blitz night" }, { timeout: 3000 })).toBeTruthy();   // no club name: this club
    expect(screen.getByRole("button", { name: "Simul" }).className).toContain("event-cancelled");
    expect(api.GET).toHaveBeenCalledWith("/api/clubs/{clubId}/events", {
      params: { path: { clubId: "c1" }, query: { from: "2026-10-26T00:00:00+00:00", to: "2026-12-07T00:00:00+00:00" } },
    });
    fireEvent.click(screen.getByRole("button", { name: "Blitz night" }));
    await waitFor(() => expect(router.state.location.pathname).toBe("/events/e1"));
  });

  it("offers New event to event managers of an active club only", async () => {
    renderPage("ACTIVE", ["EVENT_MANAGE"]);
    expect(await screen.findByRole("button", { name: "New event" }, { timeout: 3000 })).toBeTruthy();
    cleanup();
    renderPage("ACTIVE", []);
    expect(await screen.findByRole("button", { name: "Blitz night" })).toBeTruthy();
    expect(screen.queryByRole("button", { name: "New event" })).toBeNull();
    cleanup();
    renderPage("ARCHIVED", ["EVENT_MANAGE"]);
    expect(await screen.findByRole("button", { name: "Blitz night" })).toBeTruthy();
    expect(screen.queryByRole("button", { name: "New event" })).toBeNull();
  });
});
