import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { ApiError } from "@/shared/api/errors";
import { PostRedirectPage } from "./PostRedirectPage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

function renderAt(postGet: () => Promise<unknown>) {
  vi.mocked(api.GET).mockImplementation(((path: string) => {
    if (path === "/api/posts/{postId}") return postGet();
    if (path === "/api/channels/{channelId}") return Promise.resolve({ data: { id: "ch1", spaceSlug: "general", slug: "posts" } });
    return Promise.resolve({ data: { id: "c1", slug: "chess-club" } });
  }) as never);
  const router = createMemoryRouter([
    { path: "/posts/:postId", element: <PostRedirectPage /> },
    { path: "/c/:slug/s/:space/:channel", element: <p>channel page</p> },
  ], { initialEntries: ["/posts/p1"] });
  render(<QueryClientProvider client={new QueryClient()}><RouterProvider router={router} /></QueryClientProvider>);
  return router;
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("PostRedirectPage", () => {
  it("goes to the post's channel", async () => {
    const router = renderAt(() => Promise.resolve({ data: { id: "p1", channelId: "ch1", clubId: "c1" } }));
    await waitFor(() => expect(router.state.location.pathname).toBe("/c/chess-club/s/general/posts"));
  });

  it("says when the post is out of reach", async () => {
    renderAt(() => Promise.reject(new ApiError(403, { status: 403, code: "CHANNEL_READ_DENIED" })));
    expect(await screen.findByText("You cannot see this post")).toBeTruthy();
  });
});
