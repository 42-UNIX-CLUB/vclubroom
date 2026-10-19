import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { createMemoryRouter, Outlet, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { ClubEventsPage } from "./ClubEventsPage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

function renderPage(status: string, permissions: string[]) {
  vi.mocked(api.GET).mockImplementation(((path: string) => Promise.resolve({
    data: path === "/api/clubs/{clubId}/membership/me" ? { member: true, owner: false, roles: [], permissions } : [],
  })) as never);
  const router = createMemoryRouter(
    [{ element: <Outlet context={{ id: "c1", status }} />, children: [{ path: "/c/chess/events", element: <ClubEventsPage /> }] }],
    { initialEntries: ["/c/chess/events"] },
  );
  render(<QueryClientProvider client={new QueryClient()}><RouterProvider router={router} /></QueryClientProvider>);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("ClubEventsPage", () => {
  it("offers New event to event managers of an active club", async () => {
    renderPage("ACTIVE", ["EVENT_MANAGE"]);
    expect(await screen.findByRole("button", { name: "New event" })).toBeTruthy();
  });

  it("offers nothing to other members, or in an archived club", async () => {
    renderPage("ACTIVE", []);
    expect(await screen.findByText("The club calendar is coming")).toBeTruthy();
    expect(screen.queryByRole("button", { name: "New event" })).toBeNull();
    cleanup();
    renderPage("ARCHIVED", ["EVENT_MANAGE"]);
    expect(await screen.findByText("The club calendar is coming")).toBeTruthy();
    expect(screen.queryByRole("button", { name: "New event" })).toBeNull();
  });
});
