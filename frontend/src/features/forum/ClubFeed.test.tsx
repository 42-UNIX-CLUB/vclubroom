import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { ClubFeed } from "./ClubFeed";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn() } }));
const live = vi.hoisted(() => ({ handlers: new Map<string, (envelope: { type: string; payload: unknown }) => void>() }));
vi.mock("@/shared/realtime/RealtimeProvider", () => ({
  useTopic: (destination: string | null, handler: (envelope: { type: string; payload: unknown }) => void) => {
    if (destination) live.handlers.set(destination, handler);
  },
}));

const posts = [
  { id: "p2", channelId: "ch2", era: 1, title: "Budget", body: "…", commentCount: 0, author: { displayName: "Ada" } },
  { id: "p1", channelId: "ch1", era: 1, title: "Friday blitz", body: "…", commentCount: 1, author: { displayName: "Ada" } },
];

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  live.handlers.clear();
});

function renderFeed() {
  vi.mocked(api.GET).mockImplementation(((path: string) => Promise.resolve({
    data: path === "/api/clubs/{clubId}/feed" ? { items: posts } : path === "/api/reactions" ? [] : { items: [] },
  })) as never);
  render(
    <QueryClientProvider client={new QueryClient()}>
      <ClubFeed clubId="c1" era={null} channelLabel={(channelId) => `in ${channelId}`} />
    </QueryClientProvider>,
  );
}

describe("ClubFeed", () => {
  it("mixes the channels, says where each post is, and listens to each of them", async () => {
    renderFeed();

    expect(await screen.findByRole("heading", { name: "Budget" }, { timeout: 3000 })).toBeTruthy();
    expect(screen.getByText(/in ch1/)).toBeTruthy();
    expect([...live.handlers.keys()].sort()).toEqual(["/topic/channels/ch1/posts", "/topic/channels/ch2/posts"]);

    act(() => live.handlers.get("/topic/channels/ch1/posts")?.({ type: "comment.created", payload: { postId: "p1", commentCount: 5 } }));
    expect(await screen.findByRole("button", { name: /5 comments/ })).toBeTruthy();          // the feed's own cache
  });

  it("counts my comment once, even when its push arrives before the answer", async () => {
    let answer: (value: unknown) => void = () => {};
    vi.mocked(api.POST).mockReturnValue(new Promise((resolve) => { answer = resolve; }) as never);
    renderFeed();

    fireEvent.click(await screen.findByRole("button", { name: /1 comment$/ }, { timeout: 3000 }));
    fireEvent.change(await screen.findByLabelText("Write a comment"), { target: { value: "See you there" } });
    fireEvent.click(screen.getByRole("button", { name: "Comment" }));
    await waitFor(() => expect(api.POST).toHaveBeenCalled());
    act(() => live.handlers.get("/topic/channels/ch1/posts")?.({ type: "comment.created", payload: { postId: "p1", commentId: "c9", commentCount: 2 } }));
    await act(async () => answer({ data: { id: "c9", body: "See you there" } }));

    expect(await screen.findByRole("button", { name: /2 comments/ })).toBeTruthy();
    expect(screen.queryByRole("button", { name: /3 comments/ })).toBeNull();
  });
});
