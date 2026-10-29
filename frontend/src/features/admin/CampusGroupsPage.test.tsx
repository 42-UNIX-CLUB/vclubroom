import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { CampusGroupsPage } from "./CampusGroupsPage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn(), PATCH: vi.fn(), PUT: vi.fn(), DELETE: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const groups = [
  { id: "t1", name: "Tutors", slug: "manual-tutors", description: "Peer tutors", synced: false, memberCount: 1 },
  { id: "p1", name: "Pool September 2025", slug: "pool-2025-september", synced: true, memberCount: 120 },
];

function renderPage() {
  vi.mocked(api.GET).mockImplementation(((path: string) => Promise.resolve({
    data: path === "/api/campus-groups" ? groups
      : path === "/api/admin/campus-groups/{groupId}/members" ? [{ user: { id: "u1", displayName: "Ana" }, addedAt: "2026-10-01T10:00:00Z" }]
        : [],
  })) as never);
  vi.mocked(api.POST).mockResolvedValue({ data: { id: "n1", name: "BDE" } } as never);
  vi.mocked(api.DELETE).mockResolvedValue({} as never);
  render(<QueryClientProvider client={new QueryClient()}><MemoryRouter><CampusGroupsPage /></MemoryRouter></QueryClientProvider>);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("CampusGroupsPage", () => {
  it("lists manual groups with their actions and synced ones read-only", async () => {
    renderPage();

    const manual = await screen.findByRole("region", { name: "Manual groups" }, { timeout: 3000 });
    expect(await within(manual).findByText("Tutors")).toBeTruthy();
    expect(within(manual).getByRole("button", { name: "Rename" })).toBeTruthy();
    const synced = screen.getByRole("region", { name: "Synced from 42" });
    expect(within(synced).getByText("Pool September 2025")).toBeTruthy();
    expect(within(synced).queryByRole("button")).toBeNull();
  });

  it("creates a group, shows and removes a member, and deletes after confirming", async () => {
    renderPage();

    fireEvent.click(await screen.findByRole("button", { name: /New group/ }));
    fireEvent.change(within(await screen.findByRole("dialog")).getByLabelText("Name"), { target: { value: "BDE" } });
    fireEvent.click(screen.getByRole("button", { name: "Create group" }));
    await waitFor(() => expect(api.POST).toHaveBeenCalledWith("/api/admin/campus-groups", { body: { name: "BDE", description: "" } }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());

    fireEvent.click(screen.getByRole("button", { name: "Members" }));
    fireEvent.click(await screen.findByRole("button", { name: "Remove Ana" }));
    await waitFor(() => expect(api.DELETE).toHaveBeenCalledWith("/api/admin/campus-groups/{groupId}/members/{userId}",
      { params: { path: { groupId: "t1", userId: "u1" } } }));

    fireEvent.click(screen.getByRole("button", { name: "Delete" }));
    fireEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Delete group" }));
    await waitFor(() => expect(api.DELETE).toHaveBeenCalledWith("/api/admin/campus-groups/{groupId}", { params: { path: { groupId: "t1" } } }));
  });
});
