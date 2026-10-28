import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import type { Post } from "./api";
import { PostCard } from "./PostCard";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn(), PATCH: vi.fn(), DELETE: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { error: vi.fn() } }));

const base: Post = { id: "p1", channelId: "ch1", author: { displayName: "Ada" }, title: "Rules", body: "Be kind", createdAt: new Date().toISOString() };

function renderCard(post: Post) {
  vi.mocked(api.GET).mockResolvedValue({ data: [] } as never);
  vi.mocked(api.POST).mockResolvedValue({} as never);
  vi.mocked(api.PATCH).mockResolvedValue({ data: post } as never);
  vi.mocked(api.DELETE).mockResolvedValue({} as never);
  render(<QueryClientProvider client={new QueryClient()}><PostCard post={post} /></QueryClientProvider>);
}

const openMenu = async () => fireEvent.click(await screen.findByRole("button", { name: "Post actions" }));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("PostActions", () => {
  it("offers only what the viewer may do, and nothing without a right", async () => {
    renderCard({ ...base, canPin: true, canDelete: true });
    await openMenu();
    expect(await screen.findByRole("menuitem", { name: "Pin" })).toBeTruthy();
    expect(screen.getByRole("menuitem", { name: "Delete" })).toBeTruthy();
    expect(screen.queryByRole("menuitem", { name: "Edit" })).toBeNull();
    cleanup();

    renderCard(base);
    expect(screen.queryByRole("button", { name: "Post actions" })).toBeNull();
  });

  it("lets the author edit the title and text", async () => {
    renderCard({ ...base, canEdit: true, canDelete: true });
    await openMenu();
    fireEvent.click(await screen.findByRole("menuitem", { name: "Edit" }));
    const dialog = await screen.findByRole("dialog");
    fireEvent.change(within(dialog).getByLabelText("Text"), { target: { value: "Be kind, always " } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Save" }));

    await waitFor(() => expect(api.PATCH).toHaveBeenCalledWith("/api/posts/{postId}",
      { params: { path: { postId: "p1" } }, body: { title: "Rules", body: "Be kind, always" } }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  });

  it("pins, unpins and deletes after confirming", async () => {
    renderCard({ ...base, canPin: true, canDelete: true });
    await openMenu();
    fireEvent.click(await screen.findByRole("menuitem", { name: "Pin" }));
    await waitFor(() => expect(api.POST).toHaveBeenCalledWith("/api/posts/{postId}/pin", { params: { path: { postId: "p1" } } }));

    await openMenu();
    fireEvent.click(await screen.findByRole("menuitem", { name: "Delete" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/removed by a moderator/)).toBeTruthy();       // not mine: a moderator's removal
    fireEvent.click(within(dialog).getByRole("button", { name: "Delete post" }));
    await waitFor(() => expect(api.DELETE).toHaveBeenCalledWith("/api/posts/{postId}", { params: { path: { postId: "p1" } } }));
    cleanup();

    renderCard({ ...base, pinned: true, canPin: true });
    expect(screen.getByText("Pinned")).toBeTruthy();
    await openMenu();
    fireEvent.click(await screen.findByRole("menuitem", { name: "Unpin" }));
    await waitFor(() => expect(api.DELETE).toHaveBeenCalledWith("/api/posts/{postId}/pin", { params: { path: { postId: "p1" } } }));
  });
});
