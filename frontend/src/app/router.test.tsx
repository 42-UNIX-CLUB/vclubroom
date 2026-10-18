import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider, type RouteObject } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ErrorPage } from "./ErrorPage";
import { AppLayout } from "./layouts/AppLayout";
import { routes } from "./router";

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

  it("protects user pages", () => {
    const router = renderApp("/calendar");
    expect(router.state.location.pathname).toBe("/login");
    expect(router.state.location.search).toBe("?returnTo=%2Fcalendar");
  });

  it("opens the admin console on its first page for admins", () => {
    const router = renderApp("/admin", admin);
    expect(router.state.location.pathname).toBe("/admin/approvals");
    expect(screen.getByRole("heading", { name: "Club approvals" })).toBeTruthy();
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