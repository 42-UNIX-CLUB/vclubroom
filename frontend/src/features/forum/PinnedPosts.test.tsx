import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, render, screen, waitFor, within } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { ChannelFeed } from "./ChannelFeed";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn() } }));
const live = vi.hoisted(() => ({ handlers: new Map<string, (envelope: { type: string; payload: unknown }) => void>() }));
vi.mock("@/shared/realtime/RealtimeProvider", () => ({
  useTopic: (destination: string | null, handler: (envelope: { type: string; payload: unknown }) => void) => {
    if (destination) live.handlers.set(destination, handler);
  },
}));

const post = (n: number, pinned = false) => ({ id: `p${n}`, body: `Post ${n}`, pinned, author: { displayName: "Ada" }, createdAt: new Date().toISOString() });
let pinned = [post(1, true)];

function renderFeed(url: string) {
  vi.mocked(api.GET).mockImplementation(((path: string) => Promise.resolve({
    data: path === "/api/channels/{channelId}/posts/pinned" ? pinned
      : path === "/api/reactions" ? [] : { items: [post(3), post(2), post(1, true)], nextCursor: null },
  })) as never);
  const router = createMemoryRouter([{ path: "/c/:slug/s/:space/:channel", element: <ChannelFeed channel={{ id: "ch1", canWrite: false, currentEra: 2 }} /> }],
    { initialEntries: [url] });
  render(<QueryClientProvider client={new QueryClient()}><RouterProvider router={router} /></QueryClientProvider>);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("Pinned posts", () => {
  it("come first, once, and follow pushes", async () => {
    renderFeed("/c/chess/s/general/posts");

    const section = await screen.findByRole("region", { name: "Pinned" }, { timeout: 3000 });
    expect(within(section).getByText("Post 1")).toBeTruthy();
    await waitFor(() => expect(screen.getAllByText(/^Post \d$/).map((p) => p.textContent)).toEqual(["Post 1", "Post 3", "Post 2"]));

    pinned = [];
    act(() => live.handlers.get("/topic/channels/ch1/posts")?.({ type: "post.updated", payload: { postId: "p1" } }));   // unpinned
    await waitFor(() => expect(screen.queryByRole("region", { name: "Pinned" })).toBeNull());
  });

  it("are not shown in an earlier era", async () => {
    pinned = [post(1, true)];
    renderFeed("/c/chess/s/general/posts?era=1");
    expect(await screen.findByText("Post 3", {}, { timeout: 3000 })).toBeTruthy();
    expect(screen.queryByRole("region", { name: "Pinned" })).toBeNull();
    const paths = (vi.mocked(api.GET).mock.calls as unknown as [string][]).map(([path]) => path);
    expect(paths).not.toContain("/api/channels/{channelId}/posts/pinned");
  });
});
