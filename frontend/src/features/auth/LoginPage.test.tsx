import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { ApiError } from "@/shared/api/errors";
import { LoginPage } from "./LoginPage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn() } }));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function renderLogin(path = "/login") {
  const queryClient = new QueryClient();
  queryClient.setQueryData(["me"], null);   // logged out
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/clubs/new" element={<p>club wizard</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function submit(username: string, password: string) {
  fireEvent.change(screen.getByLabelText("Username"), { target: { value: username } });
  fireEvent.change(screen.getByLabelText("Password"), { target: { value: password } });
  fireEvent.click(screen.getByRole("button", { name: "Log in with password" }));
}

describe("LoginPage", () => {
  it("validates the form before calling the API", async () => {
    renderLogin();
    fireEvent.click(screen.getByRole("button", { name: "Log in with password" }));
    expect(await screen.findByText("Enter your username.")).toBeTruthy();
    expect(api.POST).not.toHaveBeenCalled();
  });

  it("explains wrong credentials", async () => {
    vi.mocked(api.POST).mockRejectedValueOnce(new ApiError(401, { status: 401, code: "INVALID_CREDENTIALS" }));
    renderLogin();
    submit("admin", "wrong");
    expect(await screen.findByText("Invalid username or password.")).toBeTruthy();
  });

  it("logs in and goes back to the page in returnTo", async () => {
    vi.mocked(api.POST).mockResolvedValueOnce({
      data: { id: "u1", login: "admin", displayName: "Administrator", provider: "LOCAL", admin: true },
    } as never);
    renderLogin("/login?returnTo=%2Fclubs%2Fnew");
    submit("admin", "secret");
    expect(await screen.findByText("club wizard")).toBeTruthy();
    expect(api.POST).toHaveBeenCalledWith("/api/auth/login", { body: { username: "admin", password: "secret" } });
  });

  it("tells the user when Login with 42 failed", () => {
    renderLogin("/login?error");
    expect(screen.getByRole("alert").textContent).toContain("Login with 42 did not complete");
  });
});