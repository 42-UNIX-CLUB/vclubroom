import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { toast } from "sonner";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { RevivalQueuePage } from "./RevivalQueuePage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const pending = [
  { id: "r1", clubId: "c1", clubName: "Film Society", clubSlug: "film-society", requesterName: "Carol Silva",
    motivation: "A new promo wants the projector back.", status: "PENDING", createdAt: new Date().toISOString() },
];

beforeEach(() => {
  vi.mocked(api.GET).mockResolvedValue({ data: { items: pending, page: 0, size: 100, totalElements: 1 } } as never);
  vi.mocked(api.POST).mockResolvedValue({ data: { name: "Film Society", currentEra: 2 } } as never);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function renderQueue() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter><RevivalQueuePage /></MemoryRouter>
    </QueryClientProvider>,
  );
}

const card = async () => (await screen.findByRole("link", { name: "Film Society" })).closest('[data-slot="card"]') as HTMLElement;

describe("RevivalQueuePage", () => {
  it("shows who asks, why, and approves", async () => {
    renderQueue();
    const film = await card();
    expect(within(film).getByText(/Carol Silva wants to revive it/)).toBeTruthy();
    expect(within(film).getByText("A new promo wants the projector back.")).toBeTruthy();
    expect(within(film).getByRole("link", { name: "Film Society" }).getAttribute("href")).toBe("/c/film-society");

    fireEvent.click(within(film).getByRole("button", { name: "Approve" }));
    await vi.waitFor(() => expect(toast.success).toHaveBeenCalledWith("Film Society is active again, in era 2."));
    expect(api.POST).toHaveBeenCalledWith("/api/admin/revival-requests/{requestId}/approve", { params: { path: { requestId: "r1" } } });
  });

  it("turns a request down only with a reason", async () => {
    renderQueue();
    fireEvent.click(within(await card()).getByRole("button", { name: "Reject" }));

    const dialog = await screen.findByRole("dialog");
    const confirm = within(dialog).getByRole("button", { name: "Turn down" });
    expect((confirm as HTMLButtonElement).disabled).toBe(true);
    fireEvent.change(within(dialog).getByLabelText("Reason"), { target: { value: "Nobody to run it yet" } });
    fireEvent.click(confirm);

    await vi.waitFor(() => expect(api.POST).toHaveBeenCalledWith("/api/admin/revival-requests/{requestId}/reject",
      { params: { path: { requestId: "r1" } }, body: { reason: "Nobody to run it yet" } }));
  });
});
