import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { toast } from "sonner";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { AdminClubsPage } from "./AdminClubsPage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const chess = { id: "c1", slug: "chess-club", name: "Chess Club", status: "ACTIVE", createdAt: "2026-10-18T10:00:00Z" };

function renderPage() {
  vi.mocked(api.GET).mockResolvedValue({ data: { items: [chess], page: 0, size: 100, totalElements: 1 } } as never);
  vi.mocked(api.POST).mockResolvedValue({ data: { ...chess, status: "ARCHIVED" } } as never);
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter><AdminClubsPage /></MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("AdminClubsPage", () => {
  it("archives an active club with a reason", async () => {
    renderPage();

    fireEvent.click(await screen.findByRole("button", { name: "Archive" }));
    const confirm = screen.getByRole("button", { name: "Archive club" }) as HTMLButtonElement;
    expect(confirm.disabled).toBe(true);                                          // a reason is required
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: " Inactive for a year " } });
    fireEvent.click(confirm);

    await waitFor(() => expect(api.POST).toHaveBeenCalledWith("/api/admin/clubs/{clubId}/archive",
      { params: { path: { clubId: "c1" } }, body: { reason: "Inactive for a year" } }));
    expect(toast.success).toHaveBeenCalledWith("Chess Club was archived.");
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  });
});
