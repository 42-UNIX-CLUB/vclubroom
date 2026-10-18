import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { afterEach, describe, expect, it } from "vitest";
import { RequireAdmin } from "./RequireAdmin";
import { RequireAuth } from "./RequireAuth";
import type { Me } from "./useMe";

afterEach(cleanup);

function LoginProbe() {
  const location = useLocation();
  return <p>login page {location.search}</p>;
}

/** Renders `element` at `path` with ["me"] already in the cache, so no request is made. */
function renderAt(path: string, me: Me | null, element: React.ReactNode) {
  const queryClient = new QueryClient();
  queryClient.setQueryData(["me"], me);
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/login" element={<LoginProbe />} />
          <Route path="*" element={element} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const student: Me = { id: "u1", login: "jdoe", displayName: "John Doe", provider: "FORTY_TWO", admin: false };

describe("RequireAuth", () => {
  it("sends logged-out visitors to /login with the page to come back to", () => {
    renderAt("/clubs/new?draft=1", null, <RequireAuth>secret</RequireAuth>);
    expect(screen.getByText("login page ?returnTo=%2Fclubs%2Fnew%3Fdraft%3D1")).toBeTruthy();
  });

  it("shows the page to logged-in users", () => {
    renderAt("/clubs/new", student, <RequireAuth>secret</RequireAuth>);
    expect(screen.getByText("secret")).toBeTruthy();
  });
});

describe("RequireAdmin", () => {
  it("tells non-admins the page is for admins only", () => {
    renderAt("/admin/approvals", student, <RequireAdmin>console</RequireAdmin>);
    expect(screen.getByText("Admins only")).toBeTruthy();
    expect(screen.queryByText("console")).toBeNull();
  });

  it("shows the console to admins", () => {
    renderAt("/admin/approvals", { ...student, admin: true }, <RequireAdmin>console</RequireAdmin>);
    expect(screen.getByText("console")).toBeTruthy();
  });
});