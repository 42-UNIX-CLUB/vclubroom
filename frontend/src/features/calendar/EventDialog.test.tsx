import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import type { ClubEvent } from "./api";
import { EventDialog } from "./EventDialog";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn(), PATCH: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock("@/features/media", () => ({ MediaPicker: () => <p>cover picker</p> }));

const spaces = [
  { id: "s1", name: "General", readLevel: "PUBLIC", archived: false, channels: [] },
  { id: "s2", name: "Board room", readLevel: "CLUB_MEMBERS", archived: false, channels: [] },
];

function renderDialog(event?: ClubEvent) {
  vi.mocked(api.GET).mockResolvedValue({ data: spaces } as never);
  vi.mocked(api.POST).mockResolvedValue({ data: { id: "e1", title: "Blitz night" } } as never);
  vi.mocked(api.PATCH).mockResolvedValue({ data: { ...event, title: "Blitz night (moved)" } } as never);
  const router = createMemoryRouter([
    { path: "/c/chess/events", element: <EventDialog clubId="c1" event={event} open onOpenChange={() => {}} /> },
    { path: "/events/:eventId", element: <p>event page</p> },
  ], { initialEntries: ["/c/chess/events"] });
  render(<QueryClientProvider client={new QueryClient()}><RouterProvider router={router} /></QueryClientProvider>);
  return router;
}

const type = (label: string, value: string) => fireEvent.change(screen.getByLabelText(label), { target: { value } });

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("EventDialog", () => {
  it("schedules in the first space, says who will see it, then opens the event", async () => {
    const router = renderDialog();

    expect(await screen.findByText("Everyone can see this event, members or not.")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Schedule event" }));
    expect(await screen.findByText("Give the event a title.")).toBeTruthy();       // nothing sent yet
    type("Title", "Blitz night");
    type("Starts", "2026-10-23T18:00");
    type("Ends", "2026-10-23T21:00");
    fireEvent.click(screen.getByRole("button", { name: "Schedule event" }));

    await waitFor(() => expect(api.POST).toHaveBeenCalledWith("/api/clubs/{clubId}/events", {
      params: { path: { clubId: "c1" } },
      body: { spaceId: "s1", title: "Blitz night", startsAt: "2026-10-23T17:00:00.000Z", endsAt: "2026-10-23T20:00:00.000Z" },
    }));
    await waitFor(() => expect(router.state.location.pathname).toBe("/events/e1"));
  });

  it("edits every field but the space", async () => {
    renderDialog({
      id: "e1", spaceId: "s2", title: "Blitz night", location: "Cluster 2",
      startsAt: "2026-10-23T17:00:00Z", endsAt: "2026-10-23T20:00:00Z",
    });

    expect(await screen.findByText("Only club members can see this event.")).toBeTruthy();
    expect((screen.getByLabelText("Title") as HTMLInputElement).value).toBe("Blitz night");
    expect((screen.getByLabelText("Starts") as HTMLInputElement).value).toBe("2026-10-23T18:00");
    expect(screen.queryByText("cover picker")).toBeNull();
    type("Title", "Blitz night (moved)");
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(api.PATCH).toHaveBeenCalledWith("/api/events/{eventId}", {
      params: { path: { eventId: "e1" } },
      body: { title: "Blitz night (moved)", location: "Cluster 2", startsAt: "2026-10-23T17:00:00.000Z", endsAt: "2026-10-23T20:00:00.000Z" },
    }));
  });
});
