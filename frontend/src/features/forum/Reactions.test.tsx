import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { toast } from "sonner";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { useChannelPosts } from "./api";
import { Reactions } from "./Reactions";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), PUT: vi.fn(), DELETE: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { error: vi.fn() } }));

const options = [{ code: "thumbs_up", emoji: "👍" }, { code: "heart", emoji: "❤️" }, { code: "party", emoji: "🎉" }];

/** Like PostCard: the bar shows the post as it is in the channel's cached pages, so optimistic changes show up. */
function Harness({ readOnly = false }: { readOnly?: boolean }) {
  const post = useChannelPosts("ch1", null).data?.pages[0]?.items?.[0];
  return post ? (
    <Reactions target={{ kind: "post", id: "p1", channelId: "ch1" }} counts={post.reactionCounts}
      mine={post.myReactions} readOnly={readOnly} />
  ) : null;
}

function renderBar(readOnly = false) {
  vi.mocked(api.GET).mockImplementation(((path: string) => Promise.resolve({
    data: path === "/api/reactions" ? options
      : { items: [{ id: "p1", reactionCounts: { party: 2, heart: 1 }, myReactions: ["party"] }], nextCursor: null },
  })) as never);
  render(<QueryClientProvider client={new QueryClient()}><Harness readOnly={readOnly} /></QueryClientProvider>);
}

/** A server answer the test releases when it wants, to look at the optimistic state first. */
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

// The first lookup waits for two queries; a cold test worker can take over the default second for that.
const chip = (name: RegExp) => screen.findByRole("button", { name }, { timeout: 3000 });

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("Reactions", () => {
  it("names codes with several underscores in full", async () => {
    vi.mocked(api.GET).mockImplementation(((path: string) => Promise.resolve({
      data: path === "/api/reactions" ? [{ code: "face_with_tears", emoji: "😂" }]
        : { items: [{ id: "p1", reactionCounts: { face_with_tears: 1 }, myReactions: [] }], nextCursor: null },
    })) as never);
    render(<QueryClientProvider client={new QueryClient()}><Harness /></QueryClientProvider>);

    expect(await chip(/face with tears/)).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Add a reaction" }));
    expect(await screen.findByRole("menuitem", { name: "face with tears" })).toBeTruthy();
  });

  it("shows the emoji used, in the set's order, with mine pressed", async () => {
    renderBar();

    const party = await chip(/party/);
    expect(party.textContent).toContain("2");
    expect(party.getAttribute("aria-pressed")).toBe("true");
    expect((await chip(/heart/)).getAttribute("aria-pressed")).toBe("false");
    expect(screen.getAllByRole("button", { pressed: false }).map((b) => b.textContent)).toEqual(["❤️heart1"]);
  });

  it("removes mine at once, then keeps what the server says", async () => {
    const answer = deferred<object>();
    vi.mocked(api.DELETE).mockReturnValue(answer.promise as never);
    renderBar();

    fireEvent.click(await chip(/party/));
    await waitFor(async () => expect((await chip(/party/)).textContent).toContain("1"));         // before the server answers
    expect((await chip(/party/)).getAttribute("aria-pressed")).toBe("false");
    expect(api.DELETE).toHaveBeenCalledWith("/api/posts/{postId}/reactions/{code}", { params: { path: { postId: "p1", code: "party" } } });

    answer.resolve({ data: { counts: { party: 3, heart: 1 }, mine: [] } });                      // someone else reacted meanwhile
    await waitFor(async () => expect((await chip(/party/)).textContent).toContain("3"));
  });

  it("rolls back when the server refuses", async () => {
    const answer = deferred<object>();
    vi.mocked(api.PUT).mockReturnValue(answer.promise as never);
    renderBar();

    fireEvent.click(await chip(/heart/));
    await waitFor(async () => expect((await chip(/heart/)).textContent).toContain("2"));
    answer.reject(new Error("Posts from previous eras are read-only."));

    await waitFor(async () => expect((await chip(/heart/)).textContent).toContain("1"));
    expect((await chip(/heart/)).getAttribute("aria-pressed")).toBe("false");
    expect(toast.error).toHaveBeenCalledWith("Posts from previous eras are read-only.");
  });

  it("adds a new emoji from the picker", async () => {
    vi.mocked(api.PUT).mockResolvedValue({ data: { counts: { party: 2, heart: 1, thumbs_up: 1 }, mine: ["party", "thumbs_up"] } } as never);
    renderBar();

    fireEvent.click(await screen.findByRole("button", { name: "Add a reaction" }));
    fireEvent.click(await screen.findByRole("menuitem", { name: "thumbs up" }));

    expect((await chip(/thumbs up/)).getAttribute("aria-pressed")).toBe("true");
    expect(api.PUT).toHaveBeenCalledWith("/api/posts/{postId}/reactions/{code}", { params: { path: { postId: "p1", code: "thumbs_up" } } });
  });

  it("only shows the counts on a read-only post", async () => {
    renderBar(true);

    expect(((await chip(/party/)) as HTMLButtonElement).disabled).toBe(true);
    expect(screen.queryByRole("button", { name: "Add a reaction" })).toBeNull();
  });
});
