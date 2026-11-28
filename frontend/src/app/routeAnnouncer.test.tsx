import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { routes } from "./router";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

afterEach(cleanup);

describe("navigating", () => {
  it("names the page in the tab, and tells screen readers where they landed", async () => {
    vi.mocked(api.GET).mockImplementation(((path: string) => Promise.resolve({
      data: path === "/api/public/categories" ? [] : { items: [], page: 0, size: 12, totalElements: 0 },
    })) as never);
    const queryClient = new QueryClient();
    queryClient.setQueryData(["me"], null);
    const router = createMemoryRouter(routes, { initialEntries: ["/login"] });
    render(<QueryClientProvider client={queryClient}><RouterProvider router={router} /></QueryClientProvider>);
    const live = document.querySelector('[aria-live="polite"][aria-atomic="true"]')!;
    expect(live.textContent).toBe("");                                     // nothing on the first page

    await act(() => router.navigate("/clubs"));
    expect(await screen.findByRole("heading", { name: "Clubs" }, { timeout: 5000 })).toBeTruthy();
    expect(document.title).toBe("Clubs · Virtual Clubroom");
    await waitFor(() => expect(live.textContent).toBe("Clubs · Virtual Clubroom"));
  });
});
