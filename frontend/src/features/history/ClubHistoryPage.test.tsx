import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { createMemoryRouter, Outlet, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { ClubHistoryPage } from "./ClubHistoryPage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

const club = {
  id: "c1", slug: "chess", name: "Chess Club", status: "ACTIVE", currentEra: 2,
  eras: [
    { number: 1, startedAt: "2025-10-01T10:00:00Z", endedAt: "2026-06-30T10:00:00Z" },
    { number: 2, startedAt: "2026-10-01T10:00:00Z" },
  ],
};
/** The feed answers with posts of the era it was asked for, so the test sees which era that was. */
function backend() {
  vi.mocked(api.GET).mockImplementation(((path: string, init?: { params: { query?: { era?: number } } }) => {
    if (path === "/api/clubs/{clubId}/timeline") return Promise.resolve({ data: { items: [] } });
    if (path === "/api/reactions") return Promise.resolve({ data: [] });
    if (path === "/api/clubs/{clubId}/feed") {
      const era = init?.params.query?.era;
      return Promise.resolve({ data: { items: [{ id: `p${era}`, channelId: "ch1", era, body: `A post of era ${era}`,
        readOnly: era !== 2, author: { displayName: "Ada" } }] } });
    }
    return Promise.reject(new Error(`unexpected GET ${path}`));
  }) as never);
}

function renderPage(url = "/c/chess/history") {
  const router = createMemoryRouter(
    [{ element: <Outlet context={club} />, children: [{ path: "/c/:slug/history", element: <ClubHistoryPage /> }] }],
    { initialEntries: [url] },
  );
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

describe("ClubHistoryPage", () => {
  it("opens on the current era, newest era first", async () => {
    backend();
    const router = renderPage();

    expect(await screen.findByText("A post of era 2")).toBeTruthy();
    await waitFor(() => expect(router.state.location.search).toBe("?era=2"));
    const eras = screen.getAllByRole("button", { pressed: true }).concat(screen.getAllByRole("button", { pressed: false }))
      .filter((button) => button.textContent?.startsWith("Era"));
    expect(eras.map((button) => [button.textContent?.slice(0, 5), button.getAttribute("aria-pressed")]))
      .toEqual([["Era 2", "true"], ["Era 1", "false"]]);
    expect(screen.queryByRole("textbox", { name: "Write a post" })).toBeNull();          // history never posts
  });

  it("shows an earlier era read-only", async () => {
    backend();
    const router = renderPage();

    fireEvent.click(await screen.findByRole("button", { name: /Era 1/ }));
    expect(await screen.findByText("A post of era 1")).toBeTruthy();
    expect(router.state.location.search).toBe("?era=1");
    expect(screen.getByRole("heading", { name: "Posts of era 1" })).toBeTruthy();
  });
});
