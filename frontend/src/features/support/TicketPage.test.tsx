import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { TicketPage } from "./TicketPage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

function renderPage() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={["/support/17"]}>
        <Routes><Route path="/support/:number" element={<TicketPage />} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("TicketPage", () => {
  it("shows the ticket as sent, with its status", async () => {
    vi.mocked(api.GET).mockResolvedValue({ data: {
        number: 17, ref: "T-17", type: "BUG", title: "The Join button does nothing", description: "I click it.\nNothing happens.",
        pageUrl: "/c/chess", status: "RECEIVED", createdAt: "2026-10-30T10:00:00Z",
      } } as never);
    renderPage();

    expect(await screen.findByRole("heading", { name: "The Join button does nothing" })).toBeTruthy();
    expect(api.GET).toHaveBeenCalledWith("/api/support/tickets/{number}", { params: { path: { number: 17 } } });
    expect(screen.getByText(/T-17 · Something doesn't work/)).toBeTruthy();
    expect(screen.getByText("Received")).toBeTruthy();
    expect(screen.getByText(/Nothing happens/)).toBeTruthy();
    expect(screen.getByRole("link", { name: "/c/chess" }).getAttribute("href")).toBe("/c/chess");
  });

  it("says when the ticket isn't there", async () => {
    vi.mocked(api.GET).mockRejectedValue(new Error("No such ticket."));
    renderPage();
    expect(await screen.findByText("Ticket not found")).toBeTruthy();
  });
});
