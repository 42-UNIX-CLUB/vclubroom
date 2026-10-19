import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { SpaceSidebar } from "./SpaceSidebar";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("SpaceSidebar", () => {
  it("lists the readable spaces with their channels and marks restricted ones", async () => {
    vi.mocked(api.GET).mockResolvedValue({ data: [
        { id: "s1", name: "General", slug: "general", readLevel: "PUBLIC", channels: [
            { id: "c1", name: "Posts", slug: "posts", type: "FORUM" }, { id: "c2", name: "Lounge", slug: "lounge", type: "CHAT" }] },
        { id: "s2", name: "Board room", slug: "board-room", readLevel: "GROUPS", channels: [{ id: "c3", name: "Minutes", slug: "minutes", type: "FORUM" }] },
      ] } as never);
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <SpaceSidebar clubId="club-1" clubSlug="chess" />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    const nav = await screen.findByRole("navigation", { name: "Spaces" });
    expect(within(nav).getByRole("link", { name: "Lounge" }).getAttribute("href")).toBe("/c/chess/s/general/lounge");
    expect(within(nav).getByRole("link", { name: "Minutes" }).getAttribute("href")).toBe("/c/chess/s/board-room/minutes");
    expect(within(nav).getByText("Board room").textContent).toContain("(restricted)");
    expect(within(nav).getByText("General").textContent).not.toContain("(restricted)");
  });
});
