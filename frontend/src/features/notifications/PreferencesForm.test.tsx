import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { toast } from "sonner";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { PreferencesForm } from "./PreferencesForm";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), PUT: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { error: vi.fn() } }));

/** The backend's defaults (R-15): in-app on for all, email for what matters most. */
const DEFAULTS: [string, boolean][] = [
  ["CLUB_CREATION_SUBMITTED", false], ["CLUB_APPROVED", true], ["CLUB_REJECTED", true], ["CLUB_ARCHIVED", true],
  ["CLUB_REVIVAL_REQUESTED", false], ["CLUB_REVIVED", true], ["JOIN_REQUEST_RECEIVED", false], ["JOIN_REQUEST_DECIDED", true],
  ["OWNERSHIP_INVITATION_RECEIVED", true], ["OWNERSHIP_CHANGED", false], ["NEW_POST", false], ["NEW_COMMENT", false],
  ["NEW_REPLY", false], ["EVENT_SCHEDULED", false], ["EVENT_UPDATED", false], ["EVENT_CANCELLED", true], ["EVENT_REMINDER", true],
];

function preferences(changes: Record<string, { inApp: boolean; email: boolean }> = {}) {
  return DEFAULTS.map(([type, email]) => ({
    type, inApp: changes[type]?.inApp ?? true, email: changes[type]?.email ?? email, defaultInApp: true, defaultEmail: email,
  }));
}

/** A backend that remembers what was saved, unless told to refuse. */
function renderForm({ admin = false, stored = {} as Record<string, { inApp: boolean; email: boolean }>, refuse = false } = {}) {
  const saved = { ...stored };
  vi.mocked(api.GET).mockImplementation(((path: string) => Promise.resolve({
    data: path === "/api/me" ? { id: "u1", login: "erin", admin } : preferences(saved),
  })) as never);
  vi.mocked(api.PUT).mockImplementation(((_path: string, { body }: { body: { type: string; inApp: boolean; email: boolean }[] }) => {
    if (refuse) return Promise.reject(new Error("Something went wrong"));
    body.forEach(({ type, inApp, email }) => { saved[type] = { inApp, email }; });
    return Promise.resolve({});
  }) as never);
  render(<QueryClientProvider client={new QueryClient()}><PreferencesForm /></QueryClientProvider>);
}

const toggle = (name: string) => screen.findByRole("switch", { name }, { timeout: 3000 });

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("PreferencesForm", () => {
  it("shows every type with its defaults, and saves a switch at once", async () => {
    renderForm();

    const email = await toggle("New posts in my clubs: email");
    expect(email.getAttribute("aria-checked")).toBe("false");
    expect(screen.getAllByText("Default: in-app only").length).toBeGreaterThan(0);
    expect(screen.queryByText("Someone asks for a new club")).toBeNull();               // admins only
    fireEvent.click(email);

    await waitFor(() => expect(email.getAttribute("aria-checked")).toBe("true"));
    await waitFor(() => expect(api.PUT).toHaveBeenCalledWith("/api/me/notification-preferences",
      { body: [{ type: "NEW_POST", inApp: true, email: true }] }));
    expect(await screen.findByText("Changed")).toBeTruthy();
  });

  it("puts a switch back when the server refuses", async () => {
    renderForm({ refuse: true });

    const inApp = await toggle("Comments on my posts: in-app");
    fireEvent.click(inApp);
    await waitFor(() => expect(toast.error).toHaveBeenCalledWith("Something went wrong"));
    expect(inApp.getAttribute("aria-checked")).toBe("true");
  });

  it("restores the defaults of what was changed; admins see their own types", async () => {
    renderForm({ admin: true, stored: { CLUB_REJECTED: { inApp: false, email: false } } });

    expect(await screen.findByText("Someone asks for a new club", {}, { timeout: 3000 })).toBeTruthy();
    expect((await toggle("A club I asked for is not approved: email")).getAttribute("aria-checked")).toBe("false");
    fireEvent.click(screen.getByRole("button", { name: "Restore the defaults" }));

    await waitFor(() => expect(api.PUT).toHaveBeenCalledWith("/api/me/notification-preferences",
      { body: [{ type: "CLUB_REJECTED", inApp: true, email: true }] }));
    await waitFor(() => expect((screen.getByRole("button", { name: "Restore the defaults" }) as HTMLButtonElement).disabled).toBe(true));
  });
});
