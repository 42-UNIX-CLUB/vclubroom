import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { createMemoryRouter, Outlet, RouterProvider } from "react-router";
import { toast } from "sonner";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { OwnershipPanel } from "./OwnershipPanel";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn(), DELETE: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock("@/features/auth", () => ({ useMe: () => ({ data: { id: "u1" } }) }));

const ada = { id: "u1", login: "ada", displayName: "Ada Founder" };
const sam = { id: "u2", login: "sam", displayName: "Sam Student" };
const owner = { member: true, owner: true, roles: [], permissions: [] };

function backend({ owners = [ada], invitations = [] as object[] } = {}) {
  vi.mocked(api.GET).mockImplementation(((path: string) => {
    if (path === "/api/clubs/{clubId}/membership/me") return Promise.resolve({ data: owner });
    if (path === "/api/clubs/{clubId}/member-summary") return Promise.resolve({ data: { memberCount: 3, owners } });
    if (path === "/api/clubs/{clubId}/ownership-invitations") return Promise.resolve({ data: invitations });
    if (path === "/api/clubs/{clubId}/members") {                              // the picker searches members (E07-S04)
      return Promise.resolve({ data: { items: [{ userId: ada.id, login: "ada", displayName: "Ada Founder", owner: true },
        { userId: sam.id, login: "sam", displayName: "Sam Student", owner: false }], totalElements: 2 } });
    }
    return Promise.reject(new Error(`unexpected GET ${path}`));
  }) as never);
  vi.mocked(api.POST).mockResolvedValue({} as never);
  vi.mocked(api.DELETE).mockResolvedValue({} as never);
}

function renderPanel() {
  const router = createMemoryRouter([
    { element: <Outlet context={{ id: "c1", slug: "rowing", name: "Rowing Club" }} />, children: [{ path: "/ownership", element: <OwnershipPanel /> }] },
    { path: "/c/rowing", element: <p>club home</p> },
  ], { initialEntries: ["/ownership"] });
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("OwnershipPanel", () => {
  it("transfers ownership to a member found by search", async () => {
    backend();
    renderPanel();

    expect(await screen.findByText("You")).toBeTruthy();
    expect((screen.getByRole("button", { name: "Transfer ownership" }) as HTMLButtonElement).disabled).toBe(true);
    fireEvent.change(screen.getByRole("searchbox", { name: "Search members" }), { target: { value: "sam" } });
    fireEvent.click(await screen.findByRole("button", { name: /Sam Student/ }));
    fireEvent.click(screen.getByRole("button", { name: "Transfer ownership" }));

    await waitFor(() => expect(api.POST).toHaveBeenCalledWith("/api/clubs/{clubId}/ownership-invitations",
      { params: { path: { clubId: "c1" } }, body: { inviteeId: "u2", transfer: true } }));
    expect(toast.success).toHaveBeenCalledWith("Invitation sent to Sam Student.");
  });

  it("lists pending invitations with a way to revoke them", async () => {
    backend({ invitations: [{ id: "i1", invitee: sam, transfer: true, expiresAt: "2026-10-25T10:00:00Z" }] });
    renderPanel();

    expect(await screen.findByText("Waiting for an answer")).toBeTruthy();
    expect(screen.getByText("Transfer")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Revoke" }));
    await waitFor(() => expect(api.DELETE).toHaveBeenCalledWith("/api/ownership-invitations/{invitationId}",
      { params: { path: { invitationId: "i1" } } }));
  });

  it("explains why the only owner cannot step down", async () => {
    backend();
    renderPanel();

    expect(await screen.findByText(/You are the only owner/)).toBeTruthy();
    expect((screen.getByRole("button", { name: "Step down as owner" }) as HTMLButtonElement).disabled).toBe(true);
  });

  it("lets a co-owner step down after confirming, then leaves the settings", async () => {
    backend({ owners: [ada, sam] });
    renderPanel();

    fireEvent.click(await screen.findByRole("button", { name: "Step down as owner" }));
    fireEvent.click(await screen.findByRole("button", { name: "Step down" }));

    expect(await screen.findByText("club home")).toBeTruthy();
    expect(api.POST).toHaveBeenCalledWith("/api/clubs/{clubId}/owners/me/step-down", { params: { path: { clubId: "c1" } } });
    expect(toast.success).toHaveBeenCalledWith("You are no longer an owner of Rowing Club.");
  });
});
