import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import { createMemoryRouter, Outlet, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { ClubSettingsIndex, ClubSettingsLayout } from "./ClubSettingsLayout";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

function backend(membership: object, pendingRequests = 0) {
  vi.mocked(api.GET).mockImplementation(((path: string) => {
    if (path === "/api/clubs/{clubId}/membership/me") return Promise.resolve({ data: membership });
    if (path === "/api/clubs/{clubId}/join-requests") return Promise.resolve({ data: Array.from({ length: pendingRequests }, (_, i) => ({ id: `r${i}` })) });
    return Promise.reject(new Error(`unexpected GET ${path}`));
  }) as never);
}

/** Like router.tsx: the club frame (here a bare Outlet with the club) → settings layout → tabs. */
function renderSettings() {
  const tab = (name: string) => <h2>{name} tab</h2>;
  const router = createMemoryRouter([{
    element: <Outlet context={{ id: "c1", name: "Debate Club" }} />,
    children: [{
      path: "/c/debate/settings",
      element: <ClubSettingsLayout />,
      children: [
        { index: true, element: <ClubSettingsIndex /> },
        { path: "profile", element: tab("Profile") },
        { path: "requests", element: tab("Join requests") },
        { path: "ownership", element: tab("Ownership") },
      ],
    }],
  }], { initialEntries: ["/c/debate/settings"] });
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return router;
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("club settings", () => {
  it("shows an owner every tab they may use, with the pending requests count, and opens the first", async () => {
    backend({ member: true, owner: true, permissions: ["CLUB_SETTINGS_MANAGE", "MEMBER_APPROVE"] }, 2);
    const router = renderSettings();

    expect(await screen.findByRole("heading", { name: "Profile tab" })).toBeTruthy();
    expect(router.state.location.pathname).toBe("/c/debate/settings/profile");
    const tabs = screen.getByRole("navigation", { name: "Club settings" });
    expect(within(tabs).getAllByRole("link").map((link) => link.textContent)).toEqual(["Profile", "Join requests2", "Ownership"]);
    expect(await within(tabs).findByLabelText("2 pending")).toBeTruthy();
  });

  it("opens Join requests for a member who may only approve", async () => {
    backend({ member: true, owner: false, permissions: ["MEMBER_APPROVE"] });
    const router = renderSettings();

    expect(await screen.findByRole("heading", { name: "Join requests tab" })).toBeTruthy();
    expect(router.state.location.pathname).toBe("/c/debate/settings/requests");
  });

  it("has nothing for a plain member", async () => {
    backend({ member: true, owner: false, permissions: [] });
    renderSettings();

    expect(await screen.findByText("Nothing to manage here")).toBeTruthy();
  });
});
