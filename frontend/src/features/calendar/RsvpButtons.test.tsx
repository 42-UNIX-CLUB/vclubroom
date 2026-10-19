import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import type { ClubEvent } from "./api";
import { RsvpButtons } from "./RsvpButtons";

vi.mock("@/shared/api/client", () => ({ api: { PUT: vi.fn(), DELETE: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const blitz: ClubEvent = { id: "e1", title: "Blitz night", status: "SCHEDULED", going: 2, interested: 1, canRsvp: true };

function renderButtons(event: ClubEvent) {
  render(<QueryClientProvider client={new QueryClient()}><RsvpButtons event={event} /></QueryClientProvider>);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("RsvpButtons", () => {
  it("answers with the counts, and pressing the current answer takes it back", async () => {
    vi.mocked(api.PUT).mockResolvedValue({ data: { ...blitz, going: 3, myRsvp: "GOING" } } as never);
    vi.mocked(api.DELETE).mockResolvedValue({ data: blitz } as never);
    renderButtons(blitz);

    const going = screen.getByRole("button", { name: "Going, 2" });
    expect(going.getAttribute("aria-pressed")).toBe("false");
    expect(screen.getByRole("button", { name: "Interested, 1" })).toBeTruthy();
    fireEvent.click(going);
    await waitFor(() => expect(api.PUT).toHaveBeenCalledWith("/api/events/{eventId}/rsvp",
      { params: { path: { eventId: "e1" } }, body: { status: "GOING" } }));

    cleanup();
    renderButtons({ ...blitz, going: 3, myRsvp: "GOING" });
    const mine = screen.getByRole("button", { name: "Going, 3" });
    expect(mine.getAttribute("aria-pressed")).toBe("true");
    fireEvent.click(mine);
    await waitFor(() => expect(api.DELETE).toHaveBeenCalledWith("/api/events/{eventId}/rsvp", { params: { path: { eventId: "e1" } } }));
  });

  it("is closed once the event is over", () => {
    renderButtons({ ...blitz, canRsvp: false });
    expect(screen.queryByRole("group", { name: "Your answer" })).toBeNull();
    expect(screen.getByText("This event is over. 2 went, 1 were interested.")).toBeTruthy();
  });
});
