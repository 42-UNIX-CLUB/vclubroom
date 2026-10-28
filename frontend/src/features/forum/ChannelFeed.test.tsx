import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { ChannelFeed, type FeedChannel } from "./ChannelFeed";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn() } }));
// The live connection is replaced by a record of who listens where, so the test can play a push.
const live = vi.hoisted(() => ({ handlers: new Map<string, (envelope: { type: string; payload: unknown }) => void>() }));
vi.mock("@/shared/realtime/RealtimeProvider", () => ({
  useTopic: (destination: string | null, handler: (envelope: { type: string; payload: unknown }) => void) => {
    if (destination) live.handlers.set(destination, handler);
  },
}));

const post = (n: number) => ({ id: `p${n}`, body: `Post ${n}`, author: { displayName: "Ada" }, createdAt: new Date().toISOString(), commentCount: 0 });

/** Two pages: the first (no cursor) has posts 3 and 2 and points to the second, which has post 1. */
function backend() {
  vi.mocked(api.GET).mockImplementation(((path: string, init: { params: { query: { cursor?: string } } }) =>
    Promise.resolve({ data: path === "/api/reactions" ? [] : init.params.query.cursor
        ? { items: [post(1)], nextCursor: null }
        : { items: [post(3), post(2)], nextCursor: "c2" } })) as never);
}

/** The query of the n-th request for posts (the cards also ask for the reaction set). */
const queryOf = (n: number) => ((vi.mocked(api.GET).mock.calls as unknown as [string, { params: { query: Record<string, unknown> } }][])
  .filter(([path]) => path === "/api/channels/{channelId}/posts")[n]?.[1])?.params.query;

function renderFeed(channel: FeedChannel, url = "/c/chess/s/general/posts") {
  const router = createMemoryRouter([{ path: "/c/:slug/s/:space/:channel", element: <ChannelFeed channel={channel} /> }], { initialEntries: [url] });
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

describe("ChannelFeed", () => {
  it("shows the newest posts, then older ones on request, and the composer to writers", async () => {
    backend();
    renderFeed({ id: "ch1", canWrite: true, currentEra: 1 });

    expect(await screen.findByText("Post 3")).toBeTruthy();
    expect(screen.getByRole("textbox", { name: "Write a post" })).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Load older posts" }));
    expect(await screen.findByText("Post 1")).toBeTruthy();
    expect(queryOf(1)).toMatchObject({ cursor: "c2", limit: 20 });
    await waitFor(() => expect(screen.queryByRole("button", { name: "Load older posts" })).toBeNull());
  });

  it("has no composer for readers", async () => {
    backend();
    renderFeed({ id: "ch1", canWrite: false, currentEra: 1 });

    expect(await screen.findByText("Post 3")).toBeTruthy();
    expect(screen.queryByRole("textbox", { name: "Write a post" })).toBeNull();
  });

  it("shows an earlier era read-only, with a way back", async () => {
    backend();
    renderFeed({ id: "ch1", canWrite: true, currentEra: 2 }, "/c/chess/s/general/posts?era=1");

    expect(await screen.findByText("Era 1: read-only history.")).toBeTruthy();
    expect(screen.queryByRole("textbox", { name: "Write a post" })).toBeNull();
    expect(queryOf(0)).toMatchObject({ era: 1 });
    expect(screen.getByRole("link", { name: "Back to era 2" })).toBeTruthy();
  });

  it("follows the channel live: new posts, comment counts and reactions", async () => {
    backend();
    renderFeed({ id: "ch1", canWrite: true, currentEra: 1 });
    expect(await screen.findByText("Post 3")).toBeTruthy();
    const push = (type: string, payload: unknown) => act(() => live.handlers.get("/topic/channels/ch1/posts")?.({ type, payload }));

    const before = vi.mocked(api.GET).mock.calls.length;
    push("post.created", { postId: "p4" });
    await waitFor(() => expect(vi.mocked(api.GET).mock.calls.length).toBeGreaterThan(before));   // the feed reloads
    push("comment.created", { postId: "p3", commentId: "c9", commentCount: 4 });
    expect(await screen.findByRole("button", { name: "4 comments" })).toBeTruthy();
    push("comment.created", { postId: "p3", commentId: "c9", commentCount: 4 });              // a repeat changes nothing
    expect(screen.getByRole("button", { name: "4 comments" })).toBeTruthy();
  });

  it("invites the first post in an empty channel", async () => {
    vi.mocked(api.GET).mockImplementation(((path: string) => Promise.resolve({
      data: path === "/api/channels/{channelId}/posts/pinned" ? [] : { items: [], nextCursor: null },
    })) as never);
    renderFeed({ id: "ch1", canWrite: true, currentEra: 1 });

    expect(await screen.findByText("Be the first to share something.")).toBeTruthy();
  });
});
