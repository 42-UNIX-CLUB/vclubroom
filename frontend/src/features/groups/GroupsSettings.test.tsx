import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { createMemoryRouter, Outlet, RouterProvider } from "react-router";
import { toast } from "sonner";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { GroupsSettings } from "./GroupsSettings";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn(), PUT: vi.fn(), DELETE: vi.fn(), PATCH: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const board = { id: "g1", name: "Board", slug: "board", memberCount: 1, description: "Runs the club" };
const ada = { id: "u1", login: "ada", displayName: "Ada Lopes" };

function backend(permissions: string[]) {
  vi.mocked(api.GET).mockImplementation(((path: string) => {
    if (path === "/api/clubs/{clubId}/membership/me") return Promise.resolve({ data: { member: true, permissions, roles: [] } });
    if (path === "/api/clubs/{clubId}/groups") return Promise.resolve({ data: [board] });
    if (path === "/api/clubs/{clubId}/groups/{groupId}/members") return Promise.resolve({ data: [{ user: ada }] });
    if (path === "/api/clubs/{clubId}/members") {                                       // the picker: owners included
      return Promise.resolve({ data: { items: [{ userId: "u9", login: "dan", displayName: "Dan Founder", owner: true }] } });
    }
    return Promise.reject(new Error(`unexpected GET ${path}`));
  }) as never);
  vi.mocked(api.POST).mockResolvedValue({ data: { id: "g2", name: "Robotics team" } } as never);
  vi.mocked(api.PUT).mockResolvedValue({} as never);
  vi.mocked(api.DELETE).mockResolvedValue({} as never);
}

function renderPage() {
  const router = createMemoryRouter([
    { path: "/c/robotics", element: <Outlet context={{ id: "c1" }} />, children: [{ path: "groups", element: <GroupsSettings /> }] },
  ], { initialEntries: ["/c/robotics/groups"] });
  render(<QueryClientProvider client={new QueryClient()}><RouterProvider router={router} /></QueryClientProvider>);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("GroupsSettings", () => {
  it("creates a group", async () => {
    backend(["GROUP_MANAGE"]);
    renderPage();

    fireEvent.change(await screen.findByLabelText("New group name", {}, { timeout: 3000 }), { target: { value: " Robotics team " } });
    fireEvent.click(screen.getByRole("button", { name: "Create group" }));
    await waitFor(() => expect(api.POST).toHaveBeenCalledWith("/api/clubs/{clubId}/groups",
      { params: { path: { clubId: "c1" } }, body: { name: "Robotics team", description: undefined } }));
    expect(toast.success).toHaveBeenCalledWith("Robotics team created.");
  });

  it("shows the members, adds anyone from the club (owners too) and removes them", async () => {
    backend(["GROUP_MANAGE"]);
    renderPage();

    const card = (await screen.findByText("Board", {}, { timeout: 3000 })).closest('[data-slot="card"]') as HTMLElement;
    expect(within(card).getByText("1 member · Runs the club")).toBeTruthy();
    fireEvent.click(within(card).getByRole("button", { name: "Members" }));
    expect(await within(card).findByText("Ada Lopes")).toBeTruthy();

    fireEvent.click(within(card).getByRole("button", { name: "Remove Ada Lopes" }));
    await waitFor(() => expect(api.DELETE).toHaveBeenCalledWith("/api/clubs/{clubId}/groups/{groupId}/members/{userId}",
      { params: { path: { clubId: "c1", groupId: "g1", userId: "u1" } } }));

    fireEvent.change(within(card).getByLabelText("Add a member to Board"), { target: { value: "dan" } });
    fireEvent.click(await within(card).findByRole("button", { name: /Dan Founder/ }, { timeout: 3000 }));
    await waitFor(() => expect(api.PUT).toHaveBeenCalledWith("/api/clubs/{clubId}/groups/{groupId}/members/{userId}",
      { params: { path: { clubId: "c1", groupId: "g1", userId: "u9" } } }));
  });

  it("deletes a group after saying what it closes", async () => {
    backend(["GROUP_MANAGE"]);
    renderPage();

    fireEvent.click(await screen.findByRole("button", { name: "Delete" }, { timeout: 3000 }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/Spaces open only to this group close for its members/)).toBeTruthy();
    fireEvent.click(within(dialog).getByRole("button", { name: "Delete group" }));
    await waitFor(() => expect(api.DELETE).toHaveBeenCalledWith("/api/clubs/{clubId}/groups/{groupId}",
      { params: { path: { clubId: "c1", groupId: "g1" } } }));
  });

  it("is for group managers only", async () => {
    backend([]);
    renderPage();
    expect(await screen.findByText("Only group managers see this", {}, { timeout: 3000 })).toBeTruthy();
  });
});
