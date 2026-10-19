import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { PostCard } from "./PostCard";

afterEach(cleanup);

describe("PostCard", () => {
  it("shows the author, the title, the Markdown body and the players", () => {
    const { container } = render(<PostCard post={{
      id: "p1", author: { displayName: "Ada Founder" }, title: "Sunset", body: "From the **roof**",
      media: [{ id: "m1", kind: "IMAGE", url: "/media-private/a.png" }, { id: "m2", kind: "VIDEO", url: "/media-private/b.mp4" }],
      createdAt: new Date().toISOString(), commentCount: 2,
    }} />);

    expect(screen.getByText("Ada Founder")).toBeTruthy();
    expect(screen.getByRole("heading", { name: "Sunset" })).toBeTruthy();
    expect(screen.getByText("roof").tagName).toBe("STRONG");
    expect(container.querySelector("img[src='/media-private/a.png']")).toBeTruthy();
    expect(container.querySelector("video[src='/media-private/b.mp4']")).toBeTruthy();
    expect(screen.getByText(/2 comments/)).toBeTruthy();
  });

  it("keeps a deleted post's place without its content", () => {
    render(<PostCard post={{ id: "p1", deleted: true, deletedByModerator: true, author: { displayName: "Ada" } }} />);
    expect(screen.getByText("This post was removed by a moderator.")).toBeTruthy();
  });
});
