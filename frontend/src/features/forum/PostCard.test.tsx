import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import type { Post } from "./api";
import { PostCard } from "./PostCard";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

const thread = { items: [{ id: "c1", body: "Lovely light", author: { displayName: "Bob" } }] };

/** The card asks for the reaction set, and for its thread once opened. */
function renderCard(post: Post) {
  vi.mocked(api.GET).mockImplementation(((path: string) =>
    Promise.resolve({ data: path === "/api/reactions" ? [{ code: "party", emoji: "🎉" }] : thread })) as never);
  return render(<QueryClientProvider client={new QueryClient()}><PostCard post={post} /></QueryClientProvider>);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("PostCard", () => {
  it("shows the author, the title, the Markdown body and the players", () => {
    const { container } = renderCard({
      id: "p1", author: { displayName: "Ada Founder" }, title: "Sunset", body: "From the **roof**",
      media: [{ id: "m1", kind: "IMAGE", url: "/media-private/a.png" }, { id: "m2", kind: "VIDEO", url: "/media-private/b.mp4" }],
      createdAt: new Date().toISOString(), commentCount: 2,
    });

    expect(screen.getByText("Ada Founder")).toBeTruthy();
    expect(screen.getByRole("heading", { name: "Sunset" })).toBeTruthy();
    expect(screen.getByText("roof").tagName).toBe("STRONG");
    expect(container.querySelector("img[src='/media-private/a.png']")).toBeTruthy();
    expect(container.querySelector("video[src='/media-private/b.mp4']")).toBeTruthy();
    expect(screen.getByText(/2 comments/)).toBeTruthy();
  });

  it("keeps a deleted post's place without its content", () => {
    renderCard({ id: "p1", deleted: true, deletedByModerator: true, author: { displayName: "Ada" } });
    expect(screen.getByText("This post was removed by a moderator.")).toBeTruthy();
  });

  it("opens its thread only when asked", async () => {
    renderCard({ id: "p1", channelId: "ch1", body: "Sunset", author: { displayName: "Ada" }, commentCount: 1 });

    expect(api.GET).not.toHaveBeenCalledWith("/api/posts/{postId}/comments", expect.anything());
    fireEvent.click(screen.getByRole("button", { name: "1 comment" }));
    expect(await screen.findByText("Lovely light")).toBeTruthy();
    expect(screen.getByRole("button", { name: "1 comment" }).getAttribute("aria-expanded")).toBe("true");
  });
});
