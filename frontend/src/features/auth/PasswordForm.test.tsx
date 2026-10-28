import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { toast } from "sonner";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { PasswordForm } from "./PasswordForm";

vi.mock("@/shared/api/client", () => ({ api: { PUT: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

function renderForm() {
  render(<QueryClientProvider client={new QueryClient()}><PasswordForm /></QueryClientProvider>);
}

const fill = (label: string, value: string) => fireEvent.change(screen.getByLabelText(label), { target: { value } });
const submit = () => screen.getByRole("button", { name: "Change password" }) as HTMLButtonElement;

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("PasswordForm", () => {
  it("needs the current password and a long enough new one, typed twice", async () => {
    vi.mocked(api.PUT).mockResolvedValue({} as never);
    renderForm();

    fill("Current password", "temporary-123");
    fill("New password", "short");
    fill("New password, again", "short");
    expect(submit().disabled).toBe(true);
    fill("New password", "my-own-secret");
    expect(screen.getByText("The two don't match.")).toBeTruthy();
    fill("New password, again", "my-own-secret");
    fireEvent.click(submit());

    await waitFor(() => expect(api.PUT).toHaveBeenCalledWith("/api/me/password",
      { body: { currentPassword: "temporary-123", newPassword: "my-own-secret" } }));
    await waitFor(() => expect((screen.getByLabelText("Current password") as HTMLInputElement).value).toBe(""));
  });

  it("says when the current password is wrong", async () => {
    vi.mocked(api.PUT).mockRejectedValue(new Error("Your current password is not right."));
    renderForm();

    fill("Current password", "nope-nope-nope");
    fill("New password", "my-own-secret");
    fill("New password, again", "my-own-secret");
    fireEvent.click(submit());
    await waitFor(() => expect(toast.error).toHaveBeenCalledWith("Your current password is not right."));
  });
});
