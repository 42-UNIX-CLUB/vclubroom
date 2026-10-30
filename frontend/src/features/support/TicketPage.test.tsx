import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { TicketPage } from "./TicketPage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
const live = vi.hoisted(() => ({ handlers: new Map<string, () => void>() }));
vi.mock("@/shared/realtime/RealtimeProvider", () => ({
  useTopic: (destination: string | null, handler: () => void) => { if (destination) live.handlers.set(destination, handler); },
}));

const ticket = {
  number: 17, ref: "T-17", type: "BUG", title: "The Join button does nothing", description: "I click it.\nNothing happens.",
  pageUrl: "/c/chess", status: "WAITING_ON_REPORTER", createdAt: "2026-10-30T10:00:00Z", canReply: true,
  messages: [
    { id: "m1", author: "TEAM", body: "Which **browser** do you use?", delivered: true, createdAt: "2026-10-30T11:00:00Z" },
    { id: "m2", author: "REPORTER", body: "Firefox 140", delivered: false, createdAt: "2026-10-30T11:05:00Z" },
  ],
};

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
  it("shows the ticket and the conversation, the team's answers in Markdown", async () => {
    vi.mocked(api.GET).mockResolvedValue({ data: ticket } as never);
    renderPage();

    expect(await screen.findByRole("heading", { name: "The Join button does nothing" })).toBeTruthy();
    expect(api.GET).toHaveBeenCalledWith("/api/support/tickets/{number}", { params: { path: { number: 17 } } });
    expect(screen.getByText(/T-17 · Something doesn't work/)).toBeTruthy();
    expect(screen.getAllByText("Waiting for you")).toHaveLength(1);
    expect(screen.getByRole("status").textContent).toBe("The team is waiting for your answer.");
    expect(screen.getByText("browser").tagName).toBe("STRONG");
    expect(screen.getByText(/on its way to the team/)).toBeTruthy();
    expect(screen.getByRole("link", { name: "/c/chess" }).getAttribute("href")).toBe("/c/chess");
  });

  it("sends a reply and asks again when a notification arrives", async () => {
    vi.mocked(api.GET).mockResolvedValue({ data: ticket } as never);
    vi.mocked(api.POST).mockResolvedValue({ data: { id: "m3", author: "REPORTER", body: "On Linux" } } as never);
    renderPage();

    fireEvent.change(await screen.findByLabelText("Your reply"), { target: { value: "On Linux" } });
    fireEvent.click(screen.getByRole("button", { name: "Send reply" }));

    await waitFor(() => expect(api.POST).toHaveBeenCalledWith("/api/support/tickets/{number}/messages",
      { params: { path: { number: 17 } }, body: { body: "On Linux" } }));
    await waitFor(() => expect((screen.getByLabelText("Your reply") as HTMLTextAreaElement).value).toBe(""));
    const calls = vi.mocked(api.GET).mock.calls.length;
    live.handlers.get("/user/queue/notifications")!();
    await waitFor(() => expect(vi.mocked(api.GET).mock.calls.length).toBeGreaterThan(calls));
  });

  it("offers a new ticket instead of a reply once closed", async () => {
    vi.mocked(api.GET).mockResolvedValue({ data: { ...ticket, status: "RESOLVED", canReply: false, messages: [] } } as never);
    renderPage();

    expect(await screen.findByText("Resolved")).toBeTruthy();
    expect(screen.queryByLabelText("Your reply")).toBeNull();
    expect(screen.getByRole("link", { name: "Send a new one" }).getAttribute("href")).toBe("/support/new");
  });

  it("says when the ticket isn't there", async () => {
    vi.mocked(api.GET).mockRejectedValue(new Error("No such ticket."));
    renderPage();
    expect(await screen.findByText("Ticket not found")).toBeTruthy();
  });
});
