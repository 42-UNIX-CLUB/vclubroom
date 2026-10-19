import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { createMemoryRouter, RouterProvider, useLocation } from "react-router";
import { toast } from "sonner";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { JoinButton, type JoinableClub } from "./JoinButton";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn(), DELETE: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const open: JoinableClub = { id: "c1", name: "Chess Club", status: "ACTIVE", joinPolicy: "OPEN" };
const visitor = { member: false, owner: false, roles: [], permissions: [] };
const member = { ...visitor, member: true };

/** GET /api/me and my membership; each refetch of the membership returns the next answer in the list. */
function backend(...memberships: object[]) {
  const answers = [...memberships];
  vi.mocked(api.GET).mockImplementation(((path: string) => {
    if (path === "/api/me") return Promise.resolve({ data: { id: "u1", login: "student", admin: false } });
    if (path === "/api/clubs/{clubId}/membership/me") return Promise.resolve({ data: answers.length > 1 ? answers.shift() : answers[0] });
    return Promise.reject(new Error(`unexpected GET ${path}`));
  }) as never);
}

function Location() {
  const location = useLocation();
  return <p data-testid="location">{location.pathname + location.search}</p>;
}

function renderButton(club: JoinableClub, url = "/c/chess-club") {
  const router = createMemoryRouter(
    [{ path: "/c/:slug", element: <><JoinButton club={club} /><Location /></> }],
    { initialEntries: [url] },
  );
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

describe("JoinButton", () => {
  it("joins an open club in one click", async () => {
    backend(visitor, member);
    vi.mocked(api.POST).mockResolvedValue({ data: { status: "JOINED", membershipId: "m1" } } as never);
    renderButton(open);

    fireEvent.click(await screen.findByRole("button", { name: "Join club" }));
    await waitFor(() => expect(vi.mocked(api.POST).mock.calls[0]?.[0]).toBe("/api/clubs/{clubId}/join"));
    expect(await screen.findByRole("button", { name: /Member/ })).toBeTruthy();    // membership refetched
    expect(toast.success).toHaveBeenCalledWith("Welcome to Chess Club!");
  });

  it("sends a request to an approval club, then waits", async () => {
    backend(visitor, { ...visitor, pendingRequest: { id: "r1" } });
    vi.mocked(api.POST).mockResolvedValue({ data: { status: "REQUESTED", requestId: "r1" } } as never);
    renderButton({ ...open, joinPolicy: "APPROVAL" });

    fireEvent.click(await screen.findByRole("button", { name: "Request to join" }));
    expect(await screen.findByRole("button", { name: /Request sent/ })).toBeTruthy();
  });

  it("cancels a pending request", async () => {
    backend({ ...visitor, pendingRequest: { id: "r1" } }, visitor);
    vi.mocked(api.DELETE).mockResolvedValue({} as never);
    renderButton({ ...open, joinPolicy: "APPROVAL" });

    fireEvent.click(await screen.findByRole("button", { name: /Request sent/ }));
    fireEvent.click(await screen.findByRole("menuitem", { name: /Cancel request/ }));

    await waitFor(() => expect(vi.mocked(api.DELETE).mock.calls[0]?.[0]).toBe("/api/clubs/{clubId}/join-requests/mine"));
    expect(await screen.findByRole("button", { name: "Request to join" })).toBeTruthy();
    expect(toast.success).toHaveBeenCalledWith("Request cancelled.");
  });

  it("lets a member leave after confirming", async () => {
    backend(member, visitor);
    vi.mocked(api.DELETE).mockResolvedValue({} as never);
    renderButton(open);

    const menu = await screen.findByRole("button", { name: /Member/ });
    fireEvent.click(menu);
    fireEvent.click(await screen.findByRole("menuitem", { name: /Leave club/ }));
    const dialog = await screen.findByRole("dialog");
    fireEvent.click(Array.from(dialog.querySelectorAll("button")).find((b) => b.textContent === "Leave club")!);

    await waitFor(() => expect(vi.mocked(api.DELETE).mock.calls[0]?.[0]).toBe("/api/clubs/{clubId}/members/me"));
    expect(await screen.findByRole("button", { name: "Join club" })).toBeTruthy();
    await waitFor(() => expect(toast.success).toHaveBeenCalledWith("You left Chess Club."));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  });

  it("explains why the last owner cannot leave", async () => {
    backend({ ...member, owner: true });
    vi.mocked(api.DELETE).mockRejectedValue(new Error("A club must keep at least one owner. Invite a co-owner first."));
    renderButton(open);

    fireEvent.click(await screen.findByRole("button", { name: /Member/ }));
    fireEvent.click(await screen.findByRole("menuitem", { name: /Leave club/ }));
    const dialog = await screen.findByRole("dialog");
    fireEvent.click(Array.from(dialog.querySelectorAll("button")).find((b) => b.textContent === "Leave club")!);

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith("A club must keep at least one owner. Invite a co-owner first."));
    expect(screen.getByRole("button", { name: /Member/ })).toBeTruthy();
  });

  it("joins by itself when the page opens with ?intent=join, once", async () => {
    backend(visitor, member);
    vi.mocked(api.POST).mockResolvedValue({ data: { status: "JOINED" } } as never);
    renderButton(open, "/c/chess-club?intent=join");

    await waitFor(() => expect(api.POST).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByTestId("location").textContent).toBe("/c/chess-club"));
    expect(await screen.findByRole("button", { name: /Member/ })).toBeTruthy();
    expect(api.POST).toHaveBeenCalledTimes(1);
  });

  it("offers nothing on a club that is not active", async () => {
    backend(visitor);
    renderButton({ ...open, status: "ARCHIVED" });

    await waitFor(() => expect(api.GET).toHaveBeenCalledWith("/api/clubs/{clubId}/membership/me", expect.anything()));
    expect(screen.queryByRole("button")).toBeNull();
  });
});
