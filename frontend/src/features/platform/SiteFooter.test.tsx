import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { SiteFooter } from "./SiteFooter";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("SiteFooter", () => {
  it("links to the API documentation, the status and the changelog, with the version", async () => {
    vi.mocked(api.GET).mockResolvedValue({ data: { current: "v1.1.0", releases: [] } } as never);
    render(<QueryClientProvider client={new QueryClient()}><MemoryRouter><SiteFooter /></MemoryRouter></QueryClientProvider>);

    const nav = screen.getByRole("navigation", { name: "Platform" });
    expect(within(nav).getByRole("link", { name: "API docs" }).getAttribute("href")).toBe("/swagger-ui.html");
    expect(within(nav).getByRole("link", { name: "Status" }).getAttribute("href")).toBe("/status");
    expect(within(nav).getByRole("link", { name: "Changelog" }).getAttribute("href")).toBe("/changelog");
    expect(await screen.findByText("Virtual Clubroom v1.1.0")).toBeTruthy();
  });
});
