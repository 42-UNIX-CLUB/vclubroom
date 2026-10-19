import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { createMemoryRouter, Outlet, RouterProvider } from "react-router";
import { toast } from "sonner";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { ApiError } from "@/shared/api/errors";
import { MembersPage } from "./MembersPage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), DELETE: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const owner = { userId: "u1", login: "ada", displayName: "Ada Founder", owner: true, joinedAt: "2026-10-18T10:00:00Z",
  roles: [{ id: "r1", name: "Owner", system: true }, { id: "r2", name: "Member", system: true }] };
const bob = { userId: "u2", login: "bob", displayName: "Bob Martins", owner: false, joinedAt: "2026-10-19T10:00:00Z",
  roles: [{ id: "r2", name: "Member", system: true }] };

function backend(permissions: string[], members: unknown = { items: [owner, bob], page: 0, size: 50, totalElements: 2 }) {
  vi.mocked(api.GET).mockImplementation(((path: string) => {
    if (path === "/api/clubs/{clubId}/membership/me") return Promise.resolve({ data: { member: true, permissions, roles: [] } });
    return members instanceof Error ? Promise.reject(members) : Promise.resolve({ data: members });
  }) as never);
  vi.mocked(api.DELETE).mockResolvedValue({} as never);
}

function renderPage() {
  const router = createMemoryRouter([
    { path: "/c/chess", element: <Outlet context={{ id: "c1", name: "Chess Club" }} />, children: [{ path: "members", element: <MembersPage /> }] },
  ], { initialEntries: ["/c/chess/members"] });
  // Like the app's client (app/queryClient.ts), a 4xx is not retried.
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><RouterProvider router={router} /></QueryClientProvider>);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("MembersPage", () => {
  it("lists members with their roles; an owner removes a member, never an owner", async () => {
    backend(["MEMBER_REMOVE"]);
    renderPage();

    const list = await screen.findByRole("list", { name: "Members" }, { timeout: 3000 });
    expect(within(list).getByText("Owner")).toBeTruthy();
    expect(within(list).queryByText("Member")).toBeNull();                          // everyone is one
    expect(screen.getByText("2 members")).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Remove Ada Founder" })).toBeNull();

    fireEvent.click(await screen.findByRole("button", { name: "Remove Bob Martins" }));
    fireEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Remove" }));
    await waitFor(() => expect(toast.success).toHaveBeenCalledWith("Bob Martins was removed."));
    expect(api.DELETE).toHaveBeenCalledWith("/api/clubs/{clubId}/members/{userId}", { params: { path: { clubId: "c1", userId: "u2" } } });
  });

  it("searches, and shows no remove buttons without the permission", async () => {
    backend([]);
    renderPage();
    await screen.findByRole("list", { name: "Members" }, { timeout: 3000 });
    expect(screen.queryByRole("button", { name: /^Remove/ })).toBeNull();

    fireEvent.change(screen.getByRole("searchbox", { name: "Search members" }), { target: { value: "bob" } });
    await waitFor(() => expect(api.GET).toHaveBeenCalledWith("/api/clubs/{clubId}/members",
      { params: { path: { clubId: "c1" }, query: { query: "bob", page: 0, size: 50 } } }));
  });

  it("tells visitors the list is for members", async () => {
    backend([], new ApiError(403, { status: 403, code: "MEMBERS_ONLY" }));
    renderPage();
    expect(await screen.findByText("Members only", {}, { timeout: 3000 })).toBeTruthy();
  });
});
