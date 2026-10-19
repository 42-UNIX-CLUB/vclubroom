import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { UpcomingEvents } from "./UpcomingEvents";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

const event = (id: string, status = "SCHEDULED") => ({ id, title: `Event ${id}`, start: "2026-11-03T18:00:00Z", status });

function renderPanel(items: object[]) {
  vi.mocked(api.GET).mockResolvedValue({ data: items } as never);
  render(<QueryClientProvider client={new QueryClient()}><MemoryRouter><UpcomingEvents clubId="c1" /></MemoryRouter></QueryClientProvider>);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("UpcomingEvents", () => {
  it("lists the next three scheduled events", async () => {
    renderPanel([event("1"), event("2", "CANCELLED"), event("3"), event("4"), event("5")]);

    expect((await screen.findAllByRole("link")).map((link) => link.textContent)).toEqual(["Event 1", "Event 3", "Event 4"]);
    expect(screen.getByRole("link", { name: "Event 1" }).getAttribute("href")).toBe("/events/1");
  });

  it("says when nothing is planned", async () => {
    renderPanel([]);
    expect(await screen.findByText("Nothing planned yet.")).toBeTruthy();
  });
});
