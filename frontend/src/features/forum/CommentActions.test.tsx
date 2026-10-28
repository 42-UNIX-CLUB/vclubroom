import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import type { Comment, Post } from "./api";
import { Comments } from "./Comments";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn(), PATCH: vi.fn(), DELETE: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { error: vi.fn() } }));
const live = vi.hoisted(() => ({ handlers: new Map<string, (envelope: { type: string; payload: unknown }) => void>() }));
vi.mock("@/shared/realtime/RealtimeProvider", () => ({
  useTopic: (destination: string | null, handler: (envelope: { type: string; payload: unknown }) => void) => {
    if (destination) live.handlers.set(destination, handler);
  },
}));

const now = new Date().toISOString();
const post: Post = { id: "p1", channelId: "ch1", commentCount: 2, readOnly: false };
let thread: Comment[] = [
  { id: "c1", postId: "p1", author: { displayName: "Ada" }, body: "I bring tea", canEdit: true, canDelete: true, createdAt: now },
  { id: "c2", postId: "p1", author: { displayName: "Bob" }, body: "I bring cake", createdAt: now },
];

function renderThread() {
  vi.mocked(api.GET).mockImplementation(((path: string) =>
    Promise.resolve({ data: path === "/api/reactions" ? [] : { items: thread, nextCursor: null } })) as never);
  vi.mocked(api.PATCH).mockResolvedValue({ data: {} } as never);
  vi.mocked(api.DELETE).mockResolvedValue({} as never);
  render(<QueryClientProvider client={new QueryClient()}><Comments post={post} /></QueryClientProvider>);
}

const item = async (text: string) => (await screen.findByText(text, {}, { timeout: 3000 })).closest("li") as HTMLElement;

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("Comment actions", () => {
  it("the author edits their comment in place; others' comments have no actions", async () => {
    renderThread();
    expect(within(await item("I bring cake")).queryByRole("button", { name: "Edit" })).toBeNull();

    fireEvent.click(within(await item("I bring tea")).getByRole("button", { name: "Edit" }));
    const box = screen.getByRole("textbox", { name: "Edit your comment" });
    expect((box as HTMLTextAreaElement).value).toBe("I bring tea");
    fireEvent.change(box, { target: { value: "I bring green tea" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(api.PATCH).toHaveBeenCalledWith("/api/comments/{commentId}",
      { params: { path: { commentId: "c1" } }, body: { body: "I bring green tea" } }));
    await waitFor(() => expect(screen.queryByRole("textbox", { name: "Edit your comment" })).toBeNull());
  });

  it("deletes after confirming, and a push about an edit reloads the thread", async () => {
    renderThread();
    fireEvent.click(within(await item("I bring tea")).getByRole("button", { name: "Delete" }));
    fireEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Delete comment" }));
    await waitFor(() => expect(api.DELETE).toHaveBeenCalledWith("/api/comments/{commentId}", { params: { path: { commentId: "c1" } } }));

    thread = [{ ...thread[0]!, body: undefined, deleted: true, canEdit: false, canDelete: false }, thread[1]!];
    act(() => live.handlers.get("/topic/posts/p1")?.({ type: "comment.deleted", payload: { postId: "p1", commentId: "c1" } }));
    expect(await screen.findByText("Deleted.")).toBeTruthy();
  });
});
