import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { ApiError } from "@/shared/api/errors";
import { PublicClubProfilePage } from "./PublicClubProfilePage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

const chess = {
  clubId: "c1", slug: "chess-club", name: "Chess Club", tagline: "Blitz on Fridays", description: "**Welcome**", category: "Games",
  tags: ["chess"], status: "ACTIVE", joinPolicy: "OPEN", memberCount: 3, currentEra: 1, foundedAt: "2026-10-18T10:00:00Z",
};

/** Who I am (null = visitor), the public profile, my membership. */
function backend(me: object | null, club: object | ApiError = chess, membership: object = { member: false }) {
  vi.mocked(api.GET).mockImplementation(((path: string) => {
    if (path === "/api/me") return me ? Promise.resolve({ data: me }) : Promise.reject(new ApiError(401, null));
    if (path === "/api/public/clubs/{slug}") return club instanceof ApiError ? Promise.reject(club) : Promise.resolve({ data: club });
    if (path === "/api/clubs/{clubId}/membership/me") return Promise.resolve({ data: membership });
    return Promise.reject(new Error(`unexpected GET ${path}`));
  }) as never);
}

function renderProfile() {
  const router = createMemoryRouter([{ path: "/clubs/:slug/about", element: <PublicClubProfilePage /> }], { initialEntries: ["/clubs/chess-club/about"] });
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}

const student = { id: "u1", login: "student", admin: false };
const href = (name: string) => screen.getByRole("link", { name }).getAttribute("href");

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("PublicClubProfilePage", () => {
  it("shows a visitor the profile and sends Join through the login, back to the club", async () => {
    backend(null);
    renderProfile();

    expect(await screen.findByRole("heading", { level: 1, name: "Chess Club" })).toBeTruthy();
    expect(screen.getByText("Welcome").tagName).toBe("STRONG");
    expect(screen.getByText("3 members")).toBeTruthy();
    expect(href("#chess")).toBe("/clubs?tag=chess");
    expect((await screen.findByRole("link", { name: "Join club" })).getAttribute("href"))
      .toBe("/login?returnTo=%2Fc%2Fchess-club%3Fintent%3Djoin");
  });

  it("lets a logged-in student join straight away", async () => {
    backend(student);
    renderProfile();

    expect((await screen.findByRole("link", { name: "Join club" })).getAttribute("href")).toBe("/c/chess-club?intent=join");
    expect(href("Open club page")).toBe("/c/chess-club");
  });

  it("sends a member to the club page", async () => {
    backend(student, chess, { member: true });
    renderProfile();

    expect(await screen.findByRole("link", { name: "Open club page" })).toBeTruthy();
    expect(screen.queryByRole("link", { name: "Join club" })).toBeNull();
  });

  it("marks an archived club and links to its history instead of joining", async () => {
    backend(null, { ...chess, status: "ARCHIVED" });
    renderProfile();

    expect(await screen.findByText("Archived")).toBeTruthy();
    expect(href("See its history")).toBe("/c/chess-club/history");
    expect(screen.queryByRole("link", { name: "Join club" })).toBeNull();
  });

  it("says when there is no such club", async () => {
    backend(null, new ApiError(404, { status: 404, code: "CLUB_NOT_FOUND" }));
    renderProfile();

    expect(await screen.findByText("Club not found")).toBeTruthy();
  });
});
