import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { StatusPage } from "./StatusPage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

function renderPage() {
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><StatusPage /></QueryClientProvider>);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("StatusPage", () => {
  it("shows each service and sums them up", async () => {
    vi.mocked(api.GET).mockResolvedValue({ data: {
      status: "DEGRADED", checkedAt: "2026-10-30T10:00:00Z", services: [
        { id: "database", name: "Database", status: "OPERATIONAL" },
        { id: "storage", name: "Images and files", status: "DOWN" },
      ],
    } } as never);
    renderPage();

    expect((await screen.findByRole("status")).textContent).toBe("Some services have problems: the rest works.");
    expect(api.GET).toHaveBeenCalledWith("/api/public/status");
    expect(screen.getByText("Database").parentElement?.textContent).toContain("Operational");
    expect(screen.getByText("Images and files").parentElement?.textContent).toContain("Down");
  });

  it("says so when nothing answers", async () => {
    vi.mocked(api.GET).mockRejectedValue(new Error("Failed to fetch"));
    renderPage();
    expect((await screen.findByRole("alert")).textContent).toContain("isn't answering");
  });
});
