import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { createMemoryRouter, Outlet, RouterProvider } from "react-router";
import { toast } from "sonner";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { JoinRequestsPage } from "./JoinRequestsPage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const approver = { member: true, owner: true, roles: [], permissions: ["MEMBER_APPROVE"] };
const sam = { id: "r1", userId: "u2", requester: { id: "u2", login: "sam", displayName: "Sam Student" }, message: "I argue a lot", createdAt: new Date().toISOString() };

/** My membership, and the queue: each refetch of the queue returns the next answer in the list. */
function backend(membership: object, ...queues: object[][]) {
  const answers = [...queues];
  vi.mocked(api.GET).mockImplementation(((path: string) => {
    if (path === "/api/clubs/{clubId}/membership/me") return Promise.resolve({ data: membership });
    if (path === "/api/clubs/{clubId}/join-requests") return Promise.resolve({ data: answers.length > 1 ? answers.shift() : answers[0] });
    return Promise.reject(new Error(`unexpected GET ${path}`));
  }) as never);
  vi.mocked(api.POST).mockResolvedValue({} as never);
}

/** The club frame passes the club as outlet context; here a bare Outlet does it. */
function renderPage() {
  const router = createMemoryRouter(
    [{ element: <Outlet context={{ id: "c1", name: "Debate Club" }} />, children: [{ path: "/requests", element: <JoinRequestsPage /> }] }],
    { initialEntries: ["/requests"] },
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

describe("JoinRequestsPage", () => {
  it("lists who asked, with their message, and approves", async () => {
    backend(approver, [sam], []);
    renderPage();

    const card = (await screen.findByText("Sam Student")).closest('[data-slot="card"]') as HTMLElement;
    expect(within(card).getByText("I argue a lot")).toBeTruthy();
    fireEvent.click(within(card).getByRole("button", { name: "Approve" }));

    await waitFor(() => expect(vi.mocked(api.POST).mock.calls[0]?.[0]).toBe("/api/clubs/{clubId}/join-requests/{requestId}/approve"));
    expect(await screen.findByText("No pending requests")).toBeTruthy();                  // the queue was refetched
    expect(toast.success).toHaveBeenCalledWith("Sam Student is now a member.");
  });

  it("declines a request", async () => {
    backend(approver, [sam], []);
    renderPage();

    fireEvent.click(await screen.findByRole("button", { name: "Decline" }));
    await waitFor(() => expect(vi.mocked(api.POST).mock.calls[0]?.[0]).toBe("/api/clubs/{clubId}/join-requests/{requestId}/reject"));
    await waitFor(() => expect(toast.success).toHaveBeenCalledWith("Request from Sam Student declined."));
  });

  it("tells members without the permission, without asking the server for the queue", async () => {
    backend({ member: true, owner: false, roles: [], permissions: [] }, []);
    renderPage();

    expect(await screen.findByText("Only approvers see join requests")).toBeTruthy();
    expect(api.GET).not.toHaveBeenCalledWith("/api/clubs/{clubId}/join-requests", expect.anything());
  });
});
