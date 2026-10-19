import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { CampusCalendarPage } from "./CampusCalendarPage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));
vi.mock("@fullcalendar/react", () => import("./fullCalendarStub"));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("CampusCalendarPage", () => {
  it("shows every readable event with its club, or only my clubs'", async () => {
    vi.mocked(api.GET).mockImplementation(((_path: string, init: { params: { query: { scope: string } } }) => Promise.resolve({
      data: init.params.query.scope === "mine"
        ? [{ id: "e1", title: "Blitz night", clubName: "Chess Club", status: "SCHEDULED" }]
        : [{ id: "e1", title: "Blitz night", clubName: "Chess Club", status: "SCHEDULED" },
           { id: "e2", title: "Go night", clubName: "Go Club", status: "SCHEDULED" }],
    })) as never);
    render(<QueryClientProvider client={new QueryClient()}><MemoryRouter><CampusCalendarPage /></MemoryRouter></QueryClientProvider>);

    expect(await screen.findByRole("button", { name: "Go Club · Go night" })).toBeTruthy();
    fireEvent.click(screen.getByRole("tab", { name: "My clubs" }));
    await waitFor(() => expect(screen.queryByRole("button", { name: "Go Club · Go night" })).toBeNull());
    expect(screen.getByRole("button", { name: "Chess Club · Blitz night" })).toBeTruthy();
  });
});
