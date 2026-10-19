import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { ApiError } from "@/shared/api/errors";
import { EventPage } from "./EventPage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const blitz = {
  id: "e1", clubId: "c1", clubSlug: "chess", clubName: "Chess Club", spaceId: "s1", title: "Blitz night",
  description: "Bring a **clock**", location: "Cluster 2", startsAt: "2026-10-23T17:00:00Z", endsAt: "2026-10-23T20:00:00Z",
  status: "SCHEDULED", canManage: true,
};

function renderPage(event: object | ApiError) {
  vi.mocked(api.GET).mockImplementation(((path: string) => path === "/api/events/{eventId}"
    ? (event instanceof ApiError ? Promise.reject(event) : Promise.resolve({ data: event }))
    : Promise.resolve({ data: [] })) as never);
  vi.mocked(api.POST).mockResolvedValue({ data: { ...blitz, status: "CANCELLED", cancelReason: "Room taken", canManage: false } } as never);
  const router = createMemoryRouter([{ path: "/events/:eventId", element: <EventPage /> }], { initialEntries: ["/events/e1"] });
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><RouterProvider router={router} /></QueryClientProvider>);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("EventPage", () => {
  it("shows when, where and what, in Lisbon time", async () => {
    renderPage({ ...blitz, canManage: false });

    expect(await screen.findByRole("heading", { name: "Blitz night" })).toBeTruthy();
    expect(screen.getByText("23 Oct 2026, 18:00 – 21:00")).toBeTruthy();
    expect(screen.getByText("Cluster 2")).toBeTruthy();
    expect(screen.getByText("clock").tagName).toBe("STRONG");
    expect(screen.getByRole("link", { name: "Chess Club" }).getAttribute("href")).toBe("/c/chess");
    expect(screen.queryByRole("button", { name: "Edit" })).toBeNull();
  });

  it("lets a manager cancel it with a reason; it stays, struck through", async () => {
    renderPage(blitz);

    fireEvent.click(await screen.findByRole("button", { name: "Cancel event" }));
    fireEvent.change(screen.getByLabelText("Reason (optional)"), { target: { value: "Room taken" } });
    fireEvent.click(screen.getAllByRole("button", { name: "Cancel event" }).at(-1)!);

    await waitFor(() => expect(api.POST).toHaveBeenCalledWith("/api/events/{eventId}/cancel",
      { params: { path: { eventId: "e1" } }, body: { reason: "Room taken" } }));
    expect(await screen.findByText("This event was cancelled")).toBeTruthy();
    expect(screen.getByText("Room taken")).toBeTruthy();
    expect(screen.getByRole("heading", { name: "Blitz night" }).className).toContain("line-through");
    expect(screen.queryByRole("button", { name: "Edit" })).toBeNull();
  });

  it("says when the event is in a space you cannot read", async () => {
    renderPage(new ApiError(403, { status: 403, code: "SPACE_READ_DENIED" }));
    expect(await screen.findByText("You cannot see this event")).toBeTruthy();
  });
});
