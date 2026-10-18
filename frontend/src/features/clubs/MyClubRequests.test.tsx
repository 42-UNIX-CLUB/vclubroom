import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { MyClubRequests } from "./MyClubRequests";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function renderList() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MyClubRequests />
    </QueryClientProvider>,
  );
}

describe("MyClubRequests", () => {
  it("shows each request's status and the reason of a rejection", async () => {
    vi.mocked(api.GET).mockResolvedValueOnce({
      data: [
        { id: "1", name: "Chess Club", status: "PENDING_APPROVAL", createdAt: new Date().toISOString() },
        { id: "2", name: "Board Games", status: "REJECTED", reviewNote: "Merged into the Games club" },
      ],
    } as never);
    renderList();
    expect(await screen.findByText("Chess Club")).toBeTruthy();
    expect(screen.getByText("Pending approval")).toBeTruthy();
    expect(screen.getByText("Rejected")).toBeTruthy();
    expect(screen.getByText("Reason: Merged into the Games club")).toBeTruthy();
  });

  it("says so when there are no requests", async () => {
    vi.mocked(api.GET).mockResolvedValueOnce({ data: [] } as never);
    renderList();
    expect(await screen.findByText("No requests yet")).toBeTruthy();
  });
});