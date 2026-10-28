import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { UsersPage } from "./UsersPage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn(), PATCH: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const users = [
  { id: "u-me", login: "admin", displayName: "Alice Admin", provider: "LOCAL", role: "ADMIN", status: "ACTIVE", createdAt: "2026-10-01T10:00:00Z" },
  { id: "u-nina", login: "nina", displayName: "Nina Partner", email: "nina@example.test", provider: "LOCAL", role: "USER", status: "ACTIVE", createdAt: "2026-10-02T10:00:00Z" },
  { id: "u-sam", login: "sam", displayName: "Sam Student", provider: "FORTY_TWO", role: "USER", status: "DISABLED", createdAt: "2026-10-03T10:00:00Z", lastLoginAt: "2026-10-20T10:00:00Z" },
];

function renderPage() {
  vi.mocked(api.GET).mockImplementation(((path: string) => Promise.resolve({
    data: path === "/api/me" ? { id: "u-me", login: "admin", admin: true } : { items: users, page: 0, size: 50, totalElements: 3 },
  })) as never);
  vi.mocked(api.POST).mockResolvedValue({ data: { ...users[1], id: "u-new", login: "omar", displayName: "Omar" } } as never);
  vi.mocked(api.PATCH).mockResolvedValue({ data: users[1] } as never);
  render(<QueryClientProvider client={new QueryClient()}><MemoryRouter><UsersPage /></MemoryRouter></QueryClientProvider>);
}

const row = async (name: string) => (await screen.findByText(name, {}, { timeout: 3000 })).closest("li") as HTMLElement;

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("UsersPage", () => {
  it("lists every account with its kind, role and status; my own row has no actions", async () => {
    renderPage();

    const me = await row("Alice Admin");
    await waitFor(() => expect(within(me).getByText("You")).toBeTruthy());
    expect(within(me).queryByRole("button")).toBeNull();
    expect(within(await row("Sam Student")).getByText("42")).toBeTruthy();
    expect(within(await row("Sam Student")).getByText("Disabled")).toBeTruthy();
    expect(within(await row("Sam Student")).queryByRole("button", { name: /Reset password/ })).toBeNull();   // a 42 account
  });

  it("makes an admin and disables an account after confirming, and enables one at once", async () => {
    renderPage();

    fireEvent.click(within(await row("Nina Partner")).getByRole("button", { name: "Make admin" }));
    expect(within(await screen.findByRole("dialog")).getByText(/admin rights when they log in again/)).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Make admin" }));
    await waitFor(() => expect(api.PATCH).toHaveBeenCalledWith("/api/admin/users/{userId}",
      { params: { path: { userId: "u-nina" } }, body: { platformRole: "ADMIN", status: undefined } }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());

    fireEvent.click(within(await row("Nina Partner")).getByRole("button", { name: "Disable" }));
    fireEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Disable account" }));
    await waitFor(() => expect(api.PATCH).toHaveBeenCalledWith("/api/admin/users/{userId}",
      { params: { path: { userId: "u-nina" } }, body: { platformRole: undefined, status: "DISABLED" } }));

    fireEvent.click(within(await row("Sam Student")).getByRole("button", { name: "Enable" }));
    await waitFor(() => expect(api.PATCH).toHaveBeenCalledWith("/api/admin/users/{userId}",
      { params: { path: { userId: "u-sam" } }, body: { platformRole: undefined, status: "ACTIVE" } }));
  });

  it("creates a local account with a generated temporary password", async () => {
    renderPage();

    fireEvent.click(await screen.findByRole("button", { name: /New local account/ }));
    const dialog = await screen.findByRole("dialog");
    const password = (within(dialog).getByLabelText("Temporary password") as HTMLInputElement).value;
    expect(password).toHaveLength(14);
    fireEvent.change(within(dialog).getByLabelText("Login"), { target: { value: "Omar" } });
    fireEvent.change(within(dialog).getByLabelText("Name"), { target: { value: "Omar Staff" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Create account" }));

    await waitFor(() => expect(api.POST).toHaveBeenCalledWith("/api/admin/users",
      { body: { username: "omar", displayName: "Omar Staff", email: undefined, temporaryPassword: password, admin: false } }));
  });

  it("resets a local account's password", async () => {
    renderPage();

    fireEvent.click(within(await row("Nina Partner")).getByRole("button", { name: /Reset password/ }));
    const dialog = await screen.findByRole("dialog");
    fireEvent.change(within(dialog).getByLabelText("Temporary password"), { target: { value: "fresh-start-1" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Set password" }));
    await waitFor(() => expect(api.POST).toHaveBeenCalledWith("/api/admin/users/{userId}/password",
      { params: { path: { userId: "u-nina" } }, body: { temporaryPassword: "fresh-start-1" } }));
  });
});
