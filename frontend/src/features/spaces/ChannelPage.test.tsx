import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { createMemoryRouter, Outlet, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { ChannelPage } from "./ChannelPage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

const posts = { id: "c1", spaceSlug: "general", spaceName: "General", name: "Posts", slug: "posts", type: "FORUM", canWrite: true, currentEra: 1 };
const lounge = { id: "c2", spaceSlug: "general", spaceName: "General", name: "Lounge", slug: "lounge", type: "CHAT", canWrite: false, currentEra: 1 };

function backend() {
  vi.mocked(api.GET).mockImplementation(((path: string, init: { params: { path: { channelId?: string } } }) => {
    if (path === "/api/clubs/{clubId}/spaces") return Promise.resolve({ data: [{ id: "s1", name: "General", slug: "general", channels: [posts, lounge] }] });
    if (path === "/api/channels/{channelId}") return Promise.resolve({ data: init.params.path.channelId === "c1" ? posts : lounge });
    if (path === "/api/channels/{channelId}/posts") return Promise.resolve({ data: { items: [], nextCursor: null } });
    return Promise.reject(new Error(`unexpected GET ${path}`));
  }) as never);
}

/** Under an outlet that provides the club, like the club frame. */
function renderChannel(url: string) {
  const router = createMemoryRouter(
    [{ element: <Outlet context={{ id: "club-1", slug: "chess" }} />, children: [{ path: "/c/:slug/s/:spaceSlug/:channelSlug", element: <ChannelPage /> }] }],
    { initialEntries: [url] },
  );
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("ChannelPage", () => {
  it("opens a forum channel: the feed of E09-S01", async () => {
    backend();
    renderChannel("/c/chess/s/general/posts");

    expect(await screen.findByRole("heading", { name: "Posts" })).toBeTruthy();
    expect(await screen.findByText("No posts yet")).toBeTruthy();
    expect(screen.queryByText("Read-only")).toBeNull();
  });

  it("opens a chat channel, read-only for this viewer", async () => {
    backend();
    renderChannel("/c/chess/s/general/lounge");

    expect(await screen.findByText("Chat is coming")).toBeTruthy();
    expect(await screen.findByText("Read-only")).toBeTruthy();
  });

  it("says when a channel does not exist or cannot be read", async () => {
    backend();
    renderChannel("/c/chess/s/general/secret");

    expect(await screen.findByText("Channel not found")).toBeTruthy();
  });
});
