import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { ApiError } from "@/shared/api/errors";
import { ClubHome } from "./ClubHome";
import { ClubLayout } from "./ClubLayout";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

const chess = {
  id: "c1", slug: "chess-club", name: "Chess Club", tagline: "Blitz on Fridays", category: "Games", tags: ["chess"],
  description: "**Welcome** to the club", status: "ACTIVE", joinPolicy: "OPEN", currentEra: 1,
  eras: [{ number: 1, startedAt: "2026-10-18T10:00:00Z", startReason: "APPROVED" }],
};
const summary = {
  memberCount: 3,
  owners: [{ id: "u1", login: "ada", displayName: "Ada Founder" }, { id: "u2", login: "bob", displayName: "Bob Co-owner" }],
};

const spaces = [{ id: "s1", name: "General", slug: "general", readLevel: "PUBLIC", channels: [
  { id: "ch1", name: "Posts", slug: "posts", type: "FORUM" }, { id: "ch2", name: "Lounge", slug: "lounge", type: "CHAT" }] }];
const visitor = { member: false, owner: false, roles: [], permissions: [] };
const owner = { member: true, owner: true, roles: [{ name: "Owner" }, { name: "Member" }], permissions: ["CLUB_SETTINGS_MANAGE", "MEMBER_APPROVE"] };

/** The API answers per path, like the backend: who I am, the club, its member summary, my membership. */
function backend(club: object | ApiError, membership: object = visitor) {
  vi.mocked(api.GET).mockImplementation(((path: string) => {
    if (path === "/api/me") return Promise.resolve({ data: { id: "u9", login: "student", admin: false } });
    if (path === "/api/clubs/{idOrSlug}") return club instanceof ApiError ? Promise.reject(club) : Promise.resolve({ data: club });
    if (path === "/api/clubs/{clubId}/member-summary") return Promise.resolve({ data: summary });
    if (path === "/api/clubs/{clubId}/membership/me") return Promise.resolve({ data: membership });
    if (path === "/api/clubs/{clubId}/spaces") return Promise.resolve({ data: spaces });
    if (path === "/api/clubs/{clubId}/events") return Promise.resolve({ data: [] });          // upcoming events (E11-S02)
    return Promise.reject(new Error(`unexpected GET ${path}`));
  }) as never);
}

function renderClubPage() {
  const router = createMemoryRouter(
    [{ path: "/c/:slug", element: <ClubLayout />, children: [{ index: true, element: <ClubHome /> }] }],
    { initialEntries: ["/c/chess-club"] },
  );
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

describe("club page", () => {
  it("shows the header, the description, the member count and the owners", async () => {
    backend(chess);
    renderClubPage();

    expect(await screen.findByRole("heading", { level: 1, name: "Chess Club" })).toBeTruthy();
    expect(screen.getByText("Active")).toBeTruthy();
    expect(screen.getByText("Blitz on Fridays")).toBeTruthy();
    expect(screen.getByText("Welcome").tagName).toBe("STRONG");
    expect(screen.getByText("#chess")).toBeTruthy();

    const about = screen.getByRole("complementary", { name: "About the club" });
    expect(await within(about).findByText("3 members")).toBeTruthy();
    expect(within(about).getByText("Ada Founder")).toBeTruthy();
    expect(within(about).getByText("@bob")).toBeTruthy();
  });

  it("shows a visitor the join button and only the public sections", async () => {
    backend(chess);
    renderClubPage();

    expect(await screen.findByRole("button", { name: "Join club" })).toBeTruthy();
    const sections = screen.getByRole("complementary", { name: "Club sections" });
    expect(within(sections).getByRole("link", { name: "History" })).toBeTruthy();
    expect(within(sections).queryByRole("link", { name: "Members" })).toBeNull();
    expect(within(sections).queryByRole("link", { name: "Settings" })).toBeNull();
    const channels = await screen.findByRole("navigation", { name: "Spaces" });           // the General space (E05-S01)
    expect(within(channels).getByRole("link", { name: "Posts" }).getAttribute("href")).toBe("/c/chess-club/s/general/posts");
  });

  it("shows an owner the member menu, the members and the settings", async () => {
    backend(chess, owner);
    renderClubPage();

    expect(await screen.findByRole("button", { name: /Member/ })).toBeTruthy();
    const sections = screen.getByRole("complementary", { name: "Club sections" });
    expect((await within(sections).findByRole("link", { name: "Members" })).getAttribute("href")).toBe("/c/chess-club/members");
    expect(within(sections).getByRole("link", { name: "Settings" })).toBeTruthy();
  });

  it("folds the channels behind a button on phones", async () => {
    backend(chess);
    renderClubPage();

    const toggle = await screen.findByRole("button", { name: "Channels" });
    const channels = document.getElementById("club-channels")!;
    expect(toggle.getAttribute("aria-expanded")).toBe("false");
    expect(channels.className).toContain("hidden md:block");                   // hidden below md, always shown above
    fireEvent.click(toggle);
    expect(toggle.getAttribute("aria-expanded")).toBe("true");
    expect(channels.className).not.toContain("hidden");
  });

  it("tells the founder that a new club is waiting for approval", async () => {
    backend({ ...chess, status: "PENDING_APPROVAL", eras: [] });
    renderClubPage();

    expect(await screen.findByText("Waiting for approval")).toBeTruthy();
    expect(screen.getByText("Pending approval")).toBeTruthy();
    expect(screen.queryByRole("complementary", { name: "About the club" })).toBeNull();   // no members yet
    expect(screen.queryByText("No posts yet")).toBeNull();
  });

  it("shows why a request was rejected", async () => {
    backend({ ...chess, status: "REJECTED", reviewNote: "Same as the Chess Society", eras: [] });
    renderClubPage();

    expect(await screen.findByText("This request was rejected")).toBeTruthy();
    expect(screen.getByText("Same as the Chess Society")).toBeTruthy();
  });

  it("shows an archived club read-only: banner, no join button, no settings", async () => {
    backend({
      ...chess, status: "ARCHIVED", reviewNote: "Inactive for a year.",
      eras: [{ number: 1, startedAt: "2026-10-18T10:00:00Z", startReason: "APPROVED", endedAt: "2026-10-19T10:00:00Z", endReason: "Inactive for a year." }],
    }, owner);
    renderClubPage();

    expect(await screen.findByText("This club is archived since 19 Oct 2026")).toBeTruthy();
    expect(screen.getByText("Inactive for a year.")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Request a revival" })).toBeTruthy();
    expect(screen.getByText("Archived")).toBeTruthy();
    const sections = screen.getByRole("complementary", { name: "Club sections" });
    expect(await within(sections).findByRole("link", { name: "Members" })).toBeTruthy();
    expect(within(sections).queryByRole("link", { name: "Settings" })).toBeNull();
    expect(screen.queryByRole("button", { name: /Member|Join/ })).toBeNull();
  });

  it("says when a club does not exist", async () => {
    backend(new ApiError(404, { status: 404, code: "CLUB_NOT_FOUND" }));
    renderClubPage();

    expect(await screen.findByText("Club not found")).toBeTruthy();
    expect(screen.getByRole("link", { name: "Browse clubs" }).getAttribute("href")).toBe("/clubs");
  });
});
