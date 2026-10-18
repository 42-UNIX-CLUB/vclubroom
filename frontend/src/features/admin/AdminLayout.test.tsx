import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { AdminLayout } from "./AdminLayout";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

afterEach(cleanup);

describe("AdminLayout", () => {
  it("shows how many club requests wait for approval", async () => {
    vi.mocked(api.GET).mockResolvedValue({ data: { items: [], page: 0, size: 100, totalElements: 3 } } as never);
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={["/admin/approvals"]}>
          <AdminLayout />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(await screen.findByLabelText("3 pending")).toBeTruthy();
    expect(screen.getByRole("link", { name: /Clubs/ }).getAttribute("href")).toBe("/admin/clubs");
  });
});
