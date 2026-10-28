import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { createMemoryRouter, Outlet, RouterProvider } from "react-router";
import { toast } from "sonner";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { RolesEditor } from "./RolesEditor";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn(), PATCH: vi.fn(), DELETE: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const catalogue = [
  { code: "EVENT_MANAGE", label: "Manage events", description: "Schedule, edit and cancel club events." },
  { code: "ROLE_MANAGE", label: "Manage roles", description: "Create roles and give them to members." },
  { code: "MEMBER_REMOVE", label: "Remove members", description: "Remove members who are not owners." },
];
const roles = [
  { id: "r0", name: "Owner", color: "#f59e0b", system: true, owner: true, permissions: ["EVENT_MANAGE", "ROLE_MANAGE", "MEMBER_REMOVE"], memberCount: 1 },
  { id: "r1", name: "Event lead", color: "#10b981", system: false, owner: false, permissions: ["EVENT_MANAGE"], memberCount: 2 },
  { id: "r2", name: "Member", color: "#64748b", system: true, owner: false, permissions: [], memberCount: 5 },
];

function renderEditor(permissions: string[]) {
  vi.mocked(api.GET).mockImplementation(((path: string) => Promise.resolve({
    data: path === "/api/permissions" ? catalogue : path === "/api/clubs/{clubId}/roles" ? roles
      : { member: true, owner: false, roles: [], permissions },
  })) as never);
  vi.mocked(api.POST).mockResolvedValue({ data: { id: "r3", name: "Board" } } as never);
  vi.mocked(api.DELETE).mockResolvedValue({} as never);
  const router = createMemoryRouter([
    { path: "/c/chess", element: <Outlet context={{ id: "c1" }} />, children: [{ path: "roles", element: <RolesEditor /> }] },
  ], { initialEntries: ["/c/chess/roles"] });
  render(<QueryClientProvider client={new QueryClient()}><RouterProvider router={router} /></QueryClientProvider>);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("RolesEditor", () => {
  it("shows the roles; Owner never changes, Member keeps its name, custom roles can go", async () => {
    renderEditor(["ROLE_MANAGE", "EVENT_MANAGE", "MEMBER_REMOVE"]);
    const list = await screen.findByRole("list", { name: "Roles" }, { timeout: 3000 });
    const [owner, lead, member] = within(list).getAllByRole("listitem");
    expect(within(owner!).getByText("Every permission, always.")).toBeTruthy();
    expect(within(owner!).queryByRole("button")).toBeNull();
    expect(within(lead!).getByText("Manage events")).toBeTruthy();
    expect(within(member!).getByRole("button", { name: "Edit permissions" })).toBeTruthy();
    expect(within(member!).queryByRole("button", { name: "Delete" })).toBeNull();

    fireEvent.click(within(lead!).getByRole("button", { name: "Delete" }));
    expect(await screen.findByText("2 members lose it, and what it allowed.")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Delete role" }));
    await waitFor(() => expect(api.DELETE).toHaveBeenCalledWith("/api/clubs/{clubId}/roles/{roleId}",
      { params: { path: { clubId: "c1", roleId: "r1" } } }));
  });

  it("creates a role with only the permissions you hold", async () => {
    renderEditor(["ROLE_MANAGE", "EVENT_MANAGE"]);
    fireEvent.click(await screen.findByRole("button", { name: "New role" }, { timeout: 3000 }));
    const dialog = await screen.findByRole("dialog");
    expect((within(dialog).getByRole("checkbox", { name: /Remove members/ }) as HTMLInputElement).disabled).toBe(true);
    fireEvent.change(within(dialog).getByLabelText("Name"), { target: { value: " Board " } });
    fireEvent.click(within(dialog).getByRole("checkbox", { name: /Manage events/ }));
    fireEvent.click(within(dialog).getByRole("button", { name: "Create role" }));

    await waitFor(() => expect(api.POST).toHaveBeenCalledWith("/api/clubs/{clubId}/roles",
      { params: { path: { clubId: "c1" } }, body: { name: "Board", color: "#10b981", permissions: ["EVENT_MANAGE"] } }));
    expect(toast.success).toHaveBeenCalledWith("Board created.");
  });
});
