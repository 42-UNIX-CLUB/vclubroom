import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { toast } from "sonner";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { uploadMedia } from "@/features/media";
import { api } from "@/shared/api/client";
import { ClubSettingsProfile } from "./ClubSettingsProfile";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), PATCH: vi.fn() } }));
vi.mock("@/features/media", () => ({ uploadMedia: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn(), info: vi.fn() } }));
vi.mock("./currentClub", () => ({
  useCurrentClub: () => ({
    id: "c1", slug: "photo-club", name: "Photo Club", tagline: "Golden hour", category: "Arts", description: "Walks",
    tags: ["photography"], status: "ACTIVE", joinPolicy: "APPROVAL",
  }),
}));

beforeEach(() => {
  vi.mocked(api.GET).mockResolvedValue({ data: ["Arts", "Games"] } as never);         // categories
  vi.mocked(api.PATCH).mockResolvedValue({ data: {} } as never);
  URL.createObjectURL = vi.fn(() => "blob:preview");                                 // not in jsdom
  URL.revokeObjectURL = vi.fn();
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function renderProfile() {
  render(<QueryClientProvider client={new QueryClient()}><ClubSettingsProfile /></QueryClientProvider>);
}

describe("ClubSettingsProfile", () => {
  it("sends only what changed, and opening the club says what it means", async () => {
    renderProfile();
    const tagline = await screen.findByLabelText("Tagline");
    expect((tagline as HTMLInputElement).value).toBe("Golden hour");

    fireEvent.change(tagline, { target: { value: "Every season" } });
    fireEvent.click(screen.getByRole("switch"));
    expect(screen.getByText(/also lets in everyone who is waiting/)).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(api.PATCH).toHaveBeenCalledWith("/api/clubs/{clubId}",
      { params: { path: { clubId: "c1" } }, body: { tagline: "Every season", joinPolicy: "OPEN" } }));
    expect(toast.success).toHaveBeenCalledWith("Saved. Anyone can join now, and everyone waiting is in.");
  });

  it("uploads a new avatar at once and attaches it on save", async () => {
    vi.mocked(uploadMedia).mockResolvedValue("m1");
    renderProfile();

    const image = new File(["png"], "logo.png", { type: "image/png" });
    fireEvent.change(await screen.findByLabelText("Avatar file"), { target: { files: [image] } });
    expect(await screen.findByText("New avatar ready: save to use it.")).toBeTruthy();
    expect(uploadMedia).toHaveBeenCalledWith(image, "CLUB_AVATAR", expect.any(Function));
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(api.PATCH).toHaveBeenCalledWith("/api/clubs/{clubId}",
      { params: { path: { clubId: "c1" } }, body: { avatarMediaId: "m1" } }));
  });
});
