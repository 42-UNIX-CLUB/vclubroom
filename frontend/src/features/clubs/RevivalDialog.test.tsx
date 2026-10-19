import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { toast } from "sonner";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { ApiError } from "@/shared/api/errors";
import { RevivalDialog } from "./RevivalDialog";

vi.mock("@/shared/api/client", () => ({ api: { POST: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const film = { id: "c1", slug: "film-society", name: "Film Society", status: "ARCHIVED", currentEra: 1 };

function renderDialog() {
  render(<QueryClientProvider client={new QueryClient()}><RevivalDialog club={film} /></QueryClientProvider>);
}

async function ask(motivation: string) {
  fireEvent.click(screen.getByRole("button", { name: "Request a revival" }));
  const dialog = await screen.findByRole("dialog");
  expect(within(dialog).getByText(/starts era 2 with you as its owner/)).toBeTruthy();
  const send = within(dialog).getByRole("button", { name: "Send request" });
  expect((send as HTMLButtonElement).disabled).toBe(true);                      // a motivation is required
  fireEvent.change(within(dialog).getByLabelText("Why bring it back?"), { target: { value: motivation } });
  fireEvent.click(send);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("RevivalDialog", () => {
  it("sends the motivation to the admins", async () => {
    vi.mocked(api.POST).mockResolvedValue({ data: { id: "r1", status: "PENDING" } } as never);
    renderDialog();
    await ask("  A new promo wants it back  ");

    await vi.waitFor(() => expect(toast.success).toHaveBeenCalledWith("Request sent. You will be notified when an admin decides."));
    expect(api.POST).toHaveBeenCalledWith("/api/clubs/{clubId}/revival-requests",
      { params: { path: { clubId: "c1" } }, body: { motivation: "A new promo wants it back" } });
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("says when you already asked", async () => {
    vi.mocked(api.POST).mockRejectedValue(new ApiError(409, { status: 409, code: "REVIVAL_ALREADY_REQUESTED" }));
    renderDialog();
    await ask("Again");

    await vi.waitFor(() => expect(toast.error).toHaveBeenCalledWith("You already asked to revive this club. An admin will answer soon."));
  });
});
