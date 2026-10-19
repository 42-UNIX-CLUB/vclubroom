import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { createMemoryRouter, RouterProvider, type RouteObject } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { ErrorPage } from "./ErrorPage";
import { AppLayout } from "./layouts/AppLayout";
import { routes } from "./router";

// The lazily loaded admin pages fetch data: answer every GET with an empty page.
vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

afterEach(cleanup);

function renderApp(path: string, me: unknown = null) {
  const queryClient = new QueryClient();
  queryClient.setQueryData(["me"], me);
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return router;
}

const admin = { id: "u1", login: "admin", displayName: "Administrator", provider: "LOCAL", admin: true };

describe("routes", () => {
  it("renders public pages inside the top bar", () => {
    renderApp("/clubs");
    expect(screen.getByRole("heading", { name: "Clubs" })).toBeTruthy();
    expect(screen.getByRole("link", { name: "Log in" }).getAttribute("href")).toBe("/login?returnTo=%2Fclubs");
  });

  it("shows a friendly 404 for unknown URLs", () => {
    renderApp("/no/such/page");
    expect(screen.getByText("Page not found")).toBeTruthy();
  });

  it("protects user pages", async () => {
    const router = renderApp("/calendar");
    // The calendar page loads lazily: the guard redirects once its route is resolved.
    await waitFor(() => expect(router.state.location.pathname).toBe("/login"));
    expect(router.state.location.search).toBe("?returnTo=%2Fcalendar");
  });

  it("opens the admin console on its first page for admins", async () => {
    vi.mocked(api.GET).mockResolvedValue({ data: { items: [], page: 0, size: 100, totalElements: 0 } } as never);
    const router = renderApp("/admin", admin);
    expect(await screen.findByRole("heading", { name: "Club approvals" }, { timeout: 5000 })).toBeTruthy();   // lazy chunk loaded
    await waitFor(() => expect(router.state.location.pathname).toBe("/admin/approvals"));
  });

  it("keeps the top bar when a page crashes", () => {
    vi.spyOn(console, "error").mockImplementation(() => {});   // React logs the thrown error
    function Boom(): never {
      throw new Error("boom");
    }
    // Same nesting as router.tsx: the page-level errorElement sits inside AppLayout.
    const crashing: RouteObject[] = [
      { element: <AppLayout />, errorElement: <ErrorPage />, children: [
          { errorElement: <ErrorPage />, children: [{ path: "*", element: <Boom /> }] },
        ] },
    ];
    const queryClient = new QueryClient();
    queryClient.setQueryData(["me"], null);
    render(
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={createMemoryRouter(crashing, { initialEntries: ["/anything"] })} />
      </QueryClientProvider>,
    );
    expect(screen.getByText("Something went wrong")).toBeTruthy();
    expect(screen.getByRole("link", { name: "Virtual Clubroom" })).toBeTruthy();
    vi.restoreAllMocks();
  });
});
