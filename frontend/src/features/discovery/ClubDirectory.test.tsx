import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { ClubDirectoryPage } from "./ClubDirectoryPage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

const chess = { clubId: "c1", slug: "chess", name: "Chess Club", category: "Games", status: "ACTIVE", memberCount: 3 };
const oldies = { clubId: "c2", slug: "oldies", name: "Old Radio", category: "Music", status: "ARCHIVED", memberCount: 1 };

/** Categories, and a directory page whose content depends on the query, like the backend. */
function backend(totalElements = 1) {
  vi.mocked(api.GET).mockImplementation(((path: string, init?: { params: { query: Record<string, unknown> } }) => {
    if (path === "/api/public/categories") return Promise.resolve({ data: ["Tech", "Games", "Music"] });
    const query = init?.params.query ?? {};
    const items = totalElements === 0 ? [] : query.status === "ALL" ? [chess, oldies] : [chess];
    return Promise.resolve({ data: { items, page: query.page, size: 12, totalElements: totalElements === 0 ? 0 : Math.max(totalElements, items.length) } });
  }) as never);
}

/** The last query the page sent to /api/public/clubs. */
type Call = [string, { params?: { query?: Record<string, unknown> } }?];
const lastQuery = () => (vi.mocked(api.GET).mock.calls as unknown as Call[])
  .filter(([path]) => path === "/api/public/clubs").at(-1)?.[1]?.params?.query;

function renderDirectory(url = "/clubs") {
  const router = createMemoryRouter([{ path: "/clubs", element: <ClubDirectoryPage /> }], { initialEntries: [url] });
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return router;
}

beforeEach(() => {
  window.scrollTo = vi.fn() as never;     // jsdom has no scrolling
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("ClubDirectoryPage", () => {
  it("reads its filters from the URL", async () => {
    backend();
    renderDirectory("/clubs?category=Games&sort=new&tag=chess");

    expect(await screen.findByRole("link", { name: /^Chess Club/ })).toBeTruthy();
    expect(lastQuery()).toMatchObject({ category: "Games", sort: "new", tag: "chess", status: "ACTIVE", page: 0, size: 12 });
    expect((await screen.findByRole("button", { name: "Games" })).getAttribute("aria-pressed")).toBe("true");
    expect(screen.getByRole("tab", { name: "Newest" }).getAttribute("aria-selected")).toBe("true");
    expect(screen.getByRole("button", { name: "Remove the tag filter chess" })).toBeTruthy();
  });

  it("searches once the user pauses typing", async () => {
    backend();
    const router = renderDirectory();

    fireEvent.change(await screen.findByRole("searchbox", { name: "Search clubs" }), { target: { value: "chess" } });
    await waitFor(() => expect(router.state.location.search).toBe("?q=chess"));
    await waitFor(() => expect(lastQuery()).toMatchObject({ q: "chess" }));
  });

  it("filters by category and includes archived clubs on request", async () => {
    backend();
    const router = renderDirectory();

    fireEvent.click(await screen.findByRole("button", { name: "Music" }));
    await waitFor(() => expect(router.state.location.search).toBe("?category=Music"));
    fireEvent.click(screen.getByRole("switch"));
    await waitFor(() => expect(router.state.location.search).toBe("?category=Music&status=ALL"));
    expect(await screen.findByText("Archived")).toBeTruthy();
  });

  it("pages through many results", async () => {
    backend(30);
    const router = renderDirectory();

    expect(await screen.findByText("Page 1 of 3")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Next" }));
    await waitFor(() => expect(router.state.location.search).toBe("?page=1"));
    await waitFor(() => expect(lastQuery()).toMatchObject({ page: 1 }));
    expect(await screen.findByText("Page 2 of 3")).toBeTruthy();
  });

  it("offers to clear filters when nothing matches", async () => {
    backend(0);
    const router = renderDirectory("/clubs?category=Tech&q=zzz");

    fireEvent.click(await screen.findByRole("button", { name: "Clear filters" }));
    await waitFor(() => expect(router.state.location.search).toBe(""));
    expect((screen.getByRole("searchbox", { name: "Search clubs" }) as HTMLInputElement).value).toBe("");
  });
});
