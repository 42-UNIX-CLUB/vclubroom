import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { CalendarFeedPanel } from "./CalendarFeedPanel";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn(), DELETE: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const URL = "https://clubroom.test/ical/feeds/s3cret.ics";

/** A backend that remembers whether I have a feed. */
function renderPanel(createdAt?: string) {
  let current = createdAt;
  vi.mocked(api.GET).mockImplementation((() => Promise.resolve({ data: { createdAt: current } })) as never);
  vi.mocked(api.POST).mockImplementation((() => {
    current = "2026-10-28T10:00:00Z";
    return Promise.resolve({ data: { url: URL, createdAt: current } });
  }) as never);
  vi.mocked(api.DELETE).mockImplementation((() => {
    current = undefined;
    return Promise.resolve({});
  }) as never);
  render(<QueryClientProvider client={new QueryClient()}><CalendarFeedPanel /></QueryClientProvider>);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("CalendarFeedPanel", () => {
  it("makes a link and shows it once, with a webcal link for calendar apps", async () => {
    renderPanel();
    fireEvent.click(await screen.findByRole("button", { name: /Get my calendar link/ }, { timeout: 3000 }));

    expect(((await screen.findByLabelText("Calendar feed link")) as HTMLInputElement).value).toBe(URL);
    expect(screen.getByRole("link", { name: "open it in your calendar app" }).getAttribute("href")).toBe("webcal://clubroom.test/ical/feeds/s3cret.ics");
    expect(await screen.findByRole("button", { name: "Get a new link" })).toBeTruthy();
  });

  it("an existing link isn't shown again; it can be replaced or turned off", async () => {
    renderPanel("2026-10-01T10:00:00Z");
    expect(await screen.findByText(/You have a calendar link, made on/, {}, { timeout: 3000 })).toBeTruthy();
    expect(screen.queryByLabelText("Calendar feed link")).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "Get a new link" }));
    fireEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Get a new link" }));
    expect(((await screen.findByLabelText("Calendar feed link")) as HTMLInputElement).value).toBe(URL);
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());

    fireEvent.click(screen.getByRole("button", { name: "Turn off" }));
    fireEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Turn off" }));
    await waitFor(() => expect(api.DELETE).toHaveBeenCalledWith("/api/me/calendar-feed"));
    expect(await screen.findByText("You have no calendar link yet.")).toBeTruthy();
  });
});
