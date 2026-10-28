import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { createMemoryRouter, Outlet, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { SpaceSettings } from "./SpaceSettings";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn(), PATCH: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const general = {
  id: "s1", name: "General", slug: "general", position: 0, readLevel: "PUBLIC", readGroupIds: [], writeLevel: "CLUB_MEMBERS",
  writeGroupIds: [], archived: false, channels: [
    { id: "ch1", type: "FORUM", name: "Posts", position: 0, archived: false },
    { id: "ch2", type: "CHAT", name: "Lounge", position: 1, archived: false },
  ],
};
const board = {
  id: "s2", name: "Board room", slug: "board-room", position: 1, readLevel: "GROUPS", readGroupIds: ["g1"], writeLevel: "GROUPS",
  writeGroupIds: ["g1"], archived: false, channels: [],
};
const old = { ...board, id: "s3", name: "Old news", slug: "old-news", position: 2, archived: true, readLevel: "CLUB_MEMBERS", readGroupIds: [] };

function backend(permissions: string[]) {
  vi.mocked(api.GET).mockImplementation(((path: string) => {
    if (path === "/api/clubs/{clubId}/membership/me") return Promise.resolve({ data: { member: true, permissions, roles: [] } });
    if (path === "/api/clubs/{clubId}/spaces") return Promise.resolve({ data: [general, board, old] });
    if (path === "/api/clubs/{clubId}/groups") return Promise.resolve({ data: [{ id: "g1", name: "Board", memberCount: 2 }] });
    return Promise.reject(new Error(`unexpected GET ${path}`));
  }) as never);
  vi.mocked(api.POST).mockResolvedValue({ data: {} } as never);
  vi.mocked(api.PATCH).mockResolvedValue({} as never);
}

function renderPage() {
  const router = createMemoryRouter([
    { path: "/c/robotics", element: <Outlet context={{ id: "c1" }} />, children: [{ path: "spaces", element: <SpaceSettings /> }] },
  ], { initialEntries: ["/c/robotics/spaces"] });
  render(<QueryClientProvider client={new QueryClient()}><RouterProvider router={router} /></QueryClientProvider>);
}

const card = async (name: string) => (await screen.findByText(name, {}, { timeout: 3000 })).closest('[data-slot="card"]') as HTMLElement;

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("SpaceSettings", () => {
  it("lists every space with who can read and post, archived ones frozen", async () => {
    backend(["SPACE_MANAGE"]);
    renderPage();

    expect(within(await card("General")).getByText("Everyone can read · Club members can post")).toBeTruthy();
    expect(within(await card("Board room")).getByText("Board can read · Board can post")).toBeTruthy();
    const archived = await card("Old news");
    expect(within(archived).getByText("Archived")).toBeTruthy();
    expect(within(archived).queryByRole("button", { name: "Edit" })).toBeNull();
    expect((within(await card("General")).getByRole("button", { name: "Move General up" }) as HTMLButtonElement).disabled).toBe(true);
  });

  it("creates a space restricted to a group", async () => {
    backend(["SPACE_MANAGE"]);
    renderPage();

    fireEvent.click(await screen.findByRole("button", { name: "New space" }, { timeout: 3000 }));
    const dialog = await screen.findByRole("dialog");
    fireEvent.change(within(dialog).getByLabelText("Name"), { target: { value: "Workshop" } });
    fireEvent.click(within(dialog).getAllByRole("radio", { name: "Some groups" })[0]!);
    const create = within(dialog).getByRole("button", { name: "Create space" }) as HTMLButtonElement;
    expect(create.disabled).toBe(true);                                                 // no group chosen yet
    fireEvent.click(await within(dialog).findByRole("checkbox", { name: /Board/ }));
    fireEvent.click(create);

    await waitFor(() => expect(api.POST).toHaveBeenCalledWith("/api/clubs/{clubId}/spaces", {
      params: { path: { clubId: "c1" } },
      body: { name: "Workshop", description: "", readRule: { level: "GROUPS", groupIds: ["g1"] }, writeRule: { level: "CLUB_MEMBERS", groupIds: [] } },
    }));
  });

  it("moves spaces and channels, and adds a channel", async () => {
    backend(["SPACE_MANAGE"]);
    renderPage();

    fireEvent.click(within(await card("Board room")).getByRole("button", { name: "Move Board room up" }));
    await waitFor(() => expect(api.PATCH).toHaveBeenCalledWith("/api/spaces/{spaceId}", { params: { path: { spaceId: "s2" } }, body: { position: 0 } }));
    fireEvent.click(within(await card("General")).getByRole("button", { name: "Move Lounge up" }));
    await waitFor(() => expect(api.PATCH).toHaveBeenCalledWith("/api/channels/{channelId}", { params: { path: { channelId: "ch2" } }, body: { position: 0 } }));

    const boardRoom = await card("Board room");
    fireEvent.change(within(boardRoom).getByLabelText("New channel name in Board room"), { target: { value: "Minutes" } });
    fireEvent.click(within(boardRoom).getByRole("button", { name: "Add channel" }));
    await waitFor(() => expect(api.POST).toHaveBeenCalledWith("/api/spaces/{spaceId}/channels",
      { params: { path: { spaceId: "s2" } }, body: { type: "FORUM", name: "Minutes" } }));
  });

  it("is for space managers only", async () => {
    backend([]);
    renderPage();
    expect(await screen.findByText("Only space managers see this", {}, { timeout: 3000 })).toBeTruthy();
  });
});
