import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { ApprovalQueuePage } from "./ApprovalQueuePage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn() } }));

const pending = [
  { id: "c1", name: "Chess Club", tagline: "Blitz on Fridays", category: "Games", tags: ["chess"], status: "PENDING_APPROVAL",
    description: "**Welcome**", createdAt: new Date().toISOString() },
  { id: "c2", name: "Duplicate Club", category: "Games", tags: [], status: "PENDING_APPROVAL" },
];

beforeEach(() => {
  vi.mocked(api.GET).mockResolvedValue({ data: { items: pending, page: 0, size: 100, totalElements: 2 } } as never);
  vi.mocked(api.POST).mockResolvedValue({ data: {} } as never);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function renderQueue() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <ApprovalQueuePage />
    </QueryClientProvider>,
  );
}

const card = async (name: string) => (await screen.findByText(name)).closest('[data-slot="card"]') as HTMLElement;

describe("ApprovalQueuePage", () => {
  it("lists pending requests with a description preview", async () => {
    renderQueue();
    const chess = await card("Chess Club");
    expect(within(chess).getByText("Blitz on Fridays")).toBeTruthy();
    fireEvent.click(within(chess).getByRole("button", { name: "Show description" }));
    expect(within(chess).getByText("Welcome").tagName).toBe("STRONG");
  });

  it("approves a club", async () => {
    renderQueue();
    fireEvent.click(within(await card("Chess Club")).getByRole("button", { name: "Approve" }));
    await vi.waitFor(() =>
      expect(api.POST).toHaveBeenCalledWith("/api/admin/clubs/{clubId}/approve", { params: { path: { clubId: "c1" } } }));
  });

  it("rejects only with a reason", async () => {
    renderQueue();
    fireEvent.click(within(await card("Duplicate Club")).getByRole("button", { name: "Reject" }));

    const dialog = await screen.findByRole("dialog");
    const confirm = within(dialog).getByRole("button", { name: "Reject club" });
    expect((confirm as HTMLButtonElement).disabled).toBe(true);   // no reason yet

    fireEvent.change(within(dialog).getByLabelText("Reason"), { target: { value: "Same as the Chess Club" } });
    fireEvent.click(confirm);
    await vi.waitFor(() => expect(api.POST).toHaveBeenCalledWith("/api/admin/clubs/{clubId}/reject",
      { params: { path: { clubId: "c2" } }, body: { reason: "Same as the Chess Club" } }));
  });

  it("says when there is nothing to review", async () => {
    vi.mocked(api.GET).mockResolvedValue({ data: { items: [], page: 0, size: 100, totalElements: 0 } } as never);
    renderQueue();
    expect(await screen.findByText("Nothing to review")).toBeTruthy();
  });
});
