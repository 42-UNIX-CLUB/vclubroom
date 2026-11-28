import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { ChangelogPage } from "./ChangelogPage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

function renderPage() {
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><ChangelogPage /></QueryClientProvider>);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("ChangelogPage", () => {
  it("lists what's coming, then each release, newest first", async () => {
    vi.mocked(api.GET).mockResolvedValue({ data: { current: "v1.0.0", releases: [
      { version: null, sections: [{ title: "Features", changes: [{ scope: "E19-S02", description: "a status page", breaking: false }] }] },
      { version: "v1.0.0", date: "2026-10-19T10:00:00Z", sections: [
        { title: "Features", changes: [{ scope: "E04-S01", description: "request club creation", breaking: false },
          { scope: "E07-S02", description: "join a club", breaking: true }] },
        { title: "Fixes", changes: [{ scope: "E11-S02", description: "build after the calendar merge", breaking: false }] }] },
    ] } } as never);
    renderPage();

    expect(await screen.findByRole("heading", { name: "Coming in the next release" }, { timeout: 3000 })).toBeTruthy();
    expect(api.GET).toHaveBeenCalledWith("/api/public/changelog");
    expect(screen.getByText("merged, not released yet · 1 change")).toBeTruthy();
    const features = screen.getByRole("region", { name: "v1.0.0: Features" });
    expect(within(features).getAllByRole("listitem").map((li) => li.textContent))
      .toEqual(["Request club creationE04-S01", "Join a clubBreakingE07-S02"]);
    expect(screen.getByText(/· 3 changes/).textContent).toContain("19 Oct 2026");
  });

  it("explains an empty changelog", async () => {
    vi.mocked(api.GET).mockResolvedValue({ data: { current: null, releases: [] } } as never);
    renderPage();
    expect(await screen.findByText("No changelog yet")).toBeTruthy();
  });
});
