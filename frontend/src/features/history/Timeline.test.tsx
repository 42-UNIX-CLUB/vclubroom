import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { Timeline } from "./Timeline";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

const ada = { id: "u1", displayName: "Ada Founder" };
const bob = { id: "u2", displayName: "Bob Member" };
const at = "2026-10-19T10:00:00Z";
const entries = [
  { id: "6", era: 1, type: "ARCHIVED", data: { reason: "Everyone graduated" }, occurredAt: at },
  { id: "5", era: 1, type: "OWNER_STEPPED_DOWN", actor: ada, subject: ada, data: {}, occurredAt: at },
  { id: "4", era: 1, type: "OWNER_ADDED", actor: ada, subject: bob, data: { reason: "INVITATION" }, occurredAt: at },
  { id: "3", era: 1, type: "OWNER_ADDED", subject: ada, data: { reason: "FOUNDER" }, occurredAt: at },
  { id: "2", era: 1, type: "FOUNDED", subject: ada, data: {}, occurredAt: at },
  { id: "1", era: 0, type: "CREATION_REQUESTED", subject: ada, data: {}, occurredAt: at },
];

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("Timeline", () => {
  it("tells each fact in a sentence, newest first", async () => {
    vi.mocked(api.GET).mockResolvedValue({ data: { items: entries, nextCursor: null } } as never);
    render(<QueryClientProvider client={new QueryClient()}><Timeline clubId="c1" /></QueryClientProvider>);

    const items = within(await screen.findByRole("list", { name: "Timeline" })).getAllByRole("listitem");
    expect(items.map((item) => item.querySelector("p")?.textContent)).toEqual([
      "Archived: Everyone graduated. Era 1 ends",
      "Ada Founder stepped down as owner",
      "Bob Member became an owner, invited by Ada Founder",
      "Ada Founder became an owner",
      "Founded by Ada Founder: era 1 begins",
      "Ada Founder asked to create the club",
    ]);
    expect(screen.queryByRole("button", { name: "Older events" })).toBeNull();
  });
});
