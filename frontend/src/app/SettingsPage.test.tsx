import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { SettingsPage } from "./SettingsPage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), PUT: vi.fn() } }));

function renderPage(provider: "LOCAL" | "FORTY_TWO") {
  vi.mocked(api.GET).mockImplementation(((path: string) => Promise.resolve({
    data: path === "/api/me" ? { id: "u1", login: "nina", provider, admin: false } : [],
  })) as never);
  render(<QueryClientProvider client={new QueryClient()}><MemoryRouter><SettingsPage /></MemoryRouter></QueryClientProvider>);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("SettingsPage", () => {
  it("offers a password change to local accounts only", async () => {
    renderPage("LOCAL");
    expect(await screen.findByRole("heading", { name: "Password" }, { timeout: 3000 })).toBeTruthy();
    expect(screen.getByRole("heading", { name: "Developer tokens" })).toBeTruthy();     // everyone (E20-S02)
    cleanup();

    renderPage("FORTY_TWO");
    expect(await screen.findByRole("heading", { name: "Notifications" })).toBeTruthy();
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(screen.queryByRole("heading", { name: "Password" })).toBeNull();
  });
});
