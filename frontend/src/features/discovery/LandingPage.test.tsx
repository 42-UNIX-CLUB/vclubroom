import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { ApiError } from "@/shared/api/errors";
import { LandingPage } from "./LandingPage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

const chess = { clubId: "c1", slug: "chess-club", name: "Chess Club", tagline: "Blitz on Fridays", category: "Games", status: "ACTIVE", memberCount: 3 };
const robotics = { clubId: "c2", slug: "robotics", name: "Robotics", category: "Tech", status: "ACTIVE", memberCount: 1 };
const landing = {
  stats: { activeClubs: 2, archivedClubs: 0, memberships: 4 },
  mostActive: [chess, robotics],
  newest: [robotics],
  categories: [{ name: "Tech", count: 1 }, { name: "Games", count: 1 }, { name: "Arts", count: 0 }],
};

/** Who I am (null = visitor, as /api/me answers 401) and the landing data. */
function backend(me: object | null, data: object = landing) {
  vi.mocked(api.GET).mockImplementation(((path: string) => {
    if (path === "/api/me") return me ? Promise.resolve({ data: me }) : Promise.reject(new ApiError(401, null));
    if (path === "/api/public/landing") return Promise.resolve({ data });
    return Promise.reject(new Error(`unexpected GET ${path}`));
  }) as never);
}

function renderLanding() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <LandingPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("LandingPage", () => {
  it("invites a visitor to browse clubs or log in with 42", async () => {
    backend(null);
    renderLanding();

    expect(screen.getByRole("heading", { level: 1 })).toBeTruthy();
    expect(screen.getByRole("link", { name: /Browse clubs/ }).getAttribute("href")).toBe("/clubs");
    expect((await screen.findByRole("link", { name: "Login with 42" })).getAttribute("href")).toBe("/oauth2/authorization/fortytwo");
    expect(screen.queryByRole("link", { name: "Start a club" })).toBeNull();
  });

  it("shows the numbers, the clubs and the categories", async () => {
    backend(null);
    renderLanding();

    const numbers = await screen.findByRole("region", { name: "Campus in numbers" });
    expect(within(numbers).getByText("2")).toBeTruthy();
    expect(within(numbers).getByText("4")).toBeTruthy();

    const card = screen.getByRole("link", { name: /^Chess Club/ });
    expect(card.getAttribute("href")).toBe("/clubs/chess-club/about");
    expect(within(card).getByText("Blitz on Fridays")).toBeTruthy();
    expect(within(card).getByText(/3 members/)).toBeTruthy();
    expect(screen.getAllByRole("link", { name: /^Robotics/ })).toHaveLength(2);         // most active and newest
    expect(screen.getByRole("link", { name: /^Games/ }).getAttribute("href")).toBe("/clubs?category=Games");
    expect(screen.getByRole("link", { name: /^Arts/ })).toBeTruthy();                   // zero counts stay clickable
  });

  it("offers a logged-in student to start a club", async () => {
    backend({ id: "u1", login: "student", admin: false });
    renderLanding();

    expect((await screen.findByRole("link", { name: "Start a club" })).getAttribute("href")).toBe("/clubs/new");
    expect(screen.queryByRole("link", { name: "Login with 42" })).toBeNull();
  });

  it("encourages the first club when there is none", async () => {
    backend(null, { stats: { activeClubs: 0, archivedClubs: 0, memberships: 0 }, mostActive: [], newest: [], categories: [] });
    renderLanding();

    expect(await screen.findByText("No clubs yet")).toBeTruthy();
    expect(screen.getByRole("link", { name: "Start a club" }).getAttribute("href")).toBe("/login?returnTo=%2Fclubs%2Fnew");
  });
});
