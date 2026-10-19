import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { toast } from "sonner";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { OwnershipInvitations } from "./OwnershipInvitations";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const transfer = {
  id: "i1", clubId: "c1", clubSlug: "rowing", clubName: "Rowing Club", transfer: true,
  invitedBy: { id: "u1", displayName: "Ada Founder" }, expiresAt: "2026-10-25T10:00:00Z",
};

/** Each GET of the inbox returns the next answer: the invitation, then nothing once it is answered. */
function backend(...answers: object[][]) {
  vi.mocked(api.GET).mockImplementation((() => Promise.resolve({ data: answers.length > 1 ? answers.shift() : answers[0] })) as never);
  vi.mocked(api.POST).mockResolvedValue({} as never);
}

function renderInbox() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter><OwnershipInvitations /></MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("OwnershipInvitations", () => {
  it("shows who invites me to which club, and accepting makes it go away", async () => {
    backend([transfer], []);
    renderInbox();

    expect(await screen.findByText(/invites you to become an owner of/)).toBeTruthy();
    expect(screen.getByRole("link", { name: "Rowing Club" }).getAttribute("href")).toBe("/c/rowing");
    expect(screen.getByText(/they step down when you accept/)).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Accept" }));

    await waitFor(() => expect(api.POST).toHaveBeenCalledWith("/api/ownership-invitations/{invitationId}/accept",
      { params: { path: { invitationId: "i1" } } }));
    await waitFor(() => expect(screen.queryByRole("list", { name: "Ownership invitations" })).toBeNull());
    expect(toast.success).toHaveBeenCalledWith("You are now an owner of Rowing Club.");
  });

  it("declines", async () => {
    backend([transfer], []);
    renderInbox();

    fireEvent.click(await screen.findByRole("button", { name: "Decline" }));
    await waitFor(() => expect(api.POST).toHaveBeenCalledWith("/api/ownership-invitations/{invitationId}/decline",
      { params: { path: { invitationId: "i1" } } }));
  });

  it("shows nothing without invitations", async () => {
    backend([]);
    renderInbox();
    await waitFor(() => expect(api.GET).toHaveBeenCalled());
    expect(screen.queryByRole("list")).toBeNull();
  });
});
