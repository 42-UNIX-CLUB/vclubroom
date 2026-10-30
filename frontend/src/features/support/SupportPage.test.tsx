import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { SupportPage } from "./SupportPage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

function renderPage() {
  render(<QueryClientProvider client={new QueryClient()}><MemoryRouter><SupportPage /></MemoryRouter></QueryClientProvider>);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("SupportPage", () => {
  it("lists my tickets with what the team says about them", async () => {
    vi.mocked(api.GET).mockResolvedValue({ data: [
        { number: 18, ref: "T-18", type: "SUGGESTION", title: "Dark calendar", status: "WAITING_ON_REPORTER", createdAt: new Date().toISOString() },
        { number: 17, ref: "T-17", type: "BUG", title: "The Join button does nothing", status: "RESOLVED", createdAt: new Date().toISOString() },
      ] } as never);
    renderPage();

    const first = await screen.findByRole("link", { name: /Dark calendar/ });
    expect(first.getAttribute("href")).toBe("/support/18");
    expect(first.textContent).toContain("Waiting for you");
    expect(first.textContent).toContain("T-18 · Suggestion");
    expect(screen.getByRole("link", { name: /The Join button does nothing/ }).textContent).toContain("Resolved");
  });

  it("explains the page when there are none", async () => {
    vi.mocked(api.GET).mockResolvedValue({ data: [] } as never);
    renderPage();
    expect(await screen.findByText("No tickets yet")).toBeTruthy();
    expect(screen.getByRole("link", { name: "New ticket" }).getAttribute("href")).toBe("/support/new");
  });
});
