import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import type { Post } from "./api";
import { Comments } from "./Comments";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn() } }));
const live = vi.hoisted(() => ({ handlers: new Map<string, (envelope: { type: string; payload: unknown }) => void>() }));
vi.mock("@/shared/realtime/RealtimeProvider", () => ({
  useTopic: (destination: string | null, handler: (envelope: { type: string; payload: unknown }) => void) => {
    if (destination) live.handlers.set(destination, handler);
  },
}));

const now = new Date().toISOString();
const thread = [
  { id: "c1", postId: "p1", author: { displayName: "Ada" }, body: "For: it is sweet", createdAt: now },
  { id: "c2", postId: "p1", author: { displayName: "Bob" }, body: "Against: it is fruit", createdAt: now },
  { id: "r1", postId: "p1", parentId: "c1", author: { displayName: "Sam" }, body: "Sweet is the point", createdAt: now },
];
const post: Post = { id: "p1", channelId: "ch1", commentCount: 3, readOnly: false };

function renderThread(target: Post, queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })) {
  vi.mocked(api.GET).mockImplementation(((path: string) =>
    Promise.resolve({ data: path === "/api/reactions" ? [] : { items: thread, nextCursor: null } })) as never);
  vi.mocked(api.POST).mockResolvedValue({ data: { id: "new" } } as never);
  render(<QueryClientProvider client={queryClient}><Comments post={target} /></QueryClientProvider>);
  return queryClient;
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("Comments", () => {
  it("shows comments oldest first with each reply under its parent", async () => {
    renderThread(post);

    const comments = await screen.findByRole("list", { name: "Comments" });
    expect(within(comments).getAllByText(/For:|Against:/).map((p) => p.textContent)).toEqual(["For: it is sweet", "Against: it is fruit"]);
    expect(within(screen.getByRole("list", { name: "Replies to Ada" })).getByText("Sweet is the point")).toBeTruthy();
  });

  it("adds a comment and counts it on the cached post", async () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(["channel", "ch1", "posts", { era: null }], { pages: [{ items: [post] }], pageParams: [undefined] });
    renderThread(post, queryClient);

    fireEvent.change(await screen.findByRole("textbox", { name: "Write a comment" }), { target: { value: "  Pass the pineapple  " } });
    fireEvent.click(screen.getByRole("button", { name: "Comment" }));

    await waitFor(() => expect(api.POST).toHaveBeenCalledWith("/api/posts/{postId}/comments",
      { params: { path: { postId: "p1" } }, body: { body: "Pass the pineapple" } }));
    await waitFor(() => expect((screen.getByRole("textbox", { name: "Write a comment" }) as HTMLTextAreaElement).value).toBe(""));
    const cached = queryClient.getQueryData<{ pages: { items: Post[] }[] }>(["channel", "ch1", "posts", { era: null }]);
    expect(cached?.pages[0]?.items[0]?.commentCount).toBe(4);
  });

  it("replies to a top-level comment", async () => {
    renderThread(post);

    const [replyToAda] = await screen.findAllByRole("button", { name: "Reply" });
    fireEvent.click(replyToAda!);
    fireEvent.change(screen.getByRole("textbox", { name: "Write a reply" }), { target: { value: "Agreed" } });
    fireEvent.click(screen.getByRole("button", { name: "Send reply" }));

    await waitFor(() => expect(api.POST).toHaveBeenCalledWith("/api/posts/{postId}/comments",
      { params: { path: { postId: "p1" } }, body: { body: "Agreed", parentId: "c1" } }));
    await waitFor(() => expect(screen.queryByRole("textbox", { name: "Write a reply" })).toBeNull());
  });

  it("reloads when someone else comments", async () => {
    renderThread(post);
    expect(await screen.findByText("Sweet is the point")).toBeTruthy();

    const threadRequests = () => (vi.mocked(api.GET).mock.calls as unknown as [string][])
      .filter(([path]) => path === "/api/posts/{postId}/comments").length;
    const before = threadRequests();
    act(() => live.handlers.get("/topic/posts/p1")?.({ type: "comment.created", payload: { postId: "p1", commentId: "c4" } }));
    await waitFor(() => expect(threadRequests()).toBeGreaterThan(before));
  });

  it("only reads on a read-only post", async () => {
    renderThread({ ...post, readOnly: true });

    expect(await screen.findByText("Sweet is the point")).toBeTruthy();
    expect(screen.queryByRole("textbox")).toBeNull();
    expect(screen.queryByRole("button", { name: "Reply" })).toBeNull();
  });
});
