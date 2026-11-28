import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { OpsCard } from "./OpsCard";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

const minutesAgo = (n: number) => new Date(Date.now() - n * 60_000).toISOString();

function renderCard(report: unknown) {
  vi.mocked(api.GET).mockResolvedValue({ data: report } as never);
  render(<QueryClientProvider client={new QueryClient()}><OpsCard /></QueryClientProvider>);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("OpsCard", () => {
  it("says all is well when nothing is late", async () => {
    renderCard({ outbox: [{ listener: "ForumNotificationListener.on", eventType: "PostPublished", count: 1, oldest: minutesAgo(0) }],
      emails: { waiting: 1, retrying: 0, failed: 0, sentLastDay: 12 },
      tickets: { unsentTickets: 0, unsentReplies: 0, lastRead: minutesAgo(0), trackerUrl: "https://tickets.example/" }, measuredAt: minutesAgo(0) });
    expect(await screen.findByText("All caught up.", {}, { timeout: 3000 })).toBeTruthy();
    expect(screen.getByText("1 being handled.")).toBeTruthy();
    expect(screen.getByText("12 sent in the last day · 1 waiting · 0 retrying")).toBeTruthy();
    expect(screen.getByText(/The tracker has every ticket\./)).toBeTruthy();
    expect(screen.getByRole("link", { name: "Open the tracker" }).getAttribute("href")).toBe("https://tickets.example/");
    expect(screen.getByText("No request refused by the rate limits in the last hour.")).toBeTruthy();
  });

  it("names stuck listeners and failed emails with the last error", async () => {
    renderCard({ outbox: [{ listener: "SpaceProvisioner.on", eventType: "ClubApproved", count: 3, oldest: minutesAgo(30) }],
      emails: { waiting: 0, retrying: 2, failed: 1, sentLastDay: 0, lastError: "SMTP delivery failed: 535 auth" }, refusedApiRequests: 42, measuredAt: minutesAgo(0) });
    expect(await screen.findByText("Something is late: see below.", {}, { timeout: 3000 })).toBeTruthy();
    expect(within(screen.getByRole("list", { name: "Stuck listeners" })).getByText(/SpaceProvisioner.on \(ClubApproved\): 3/)).toBeTruthy();
    expect(screen.getByText("1 given up after 5 attempts.")).toBeTruthy();
    expect(screen.getByText("Last error: SMTP delivery failed: 535 auth")).toBeTruthy();
    expect(screen.getByText("42 requests refused by the rate limits in the last hour.")).toBeTruthy();
  });

  it("says when the tracker is out of reach", async () => {
    renderCard({ outbox: [], emails: { waiting: 0, retrying: 0, failed: 0, sentLastDay: 3 },
      tickets: { unsentTickets: 2, unsentReplies: 1, oldestUnsent: minutesAgo(25), lastRead: minutesAgo(25),
        lastError: "Forgejo unreachable: Connection refused" }, measuredAt: minutesAgo(0) });
    expect(await screen.findByText("Something is late: see below.", {}, { timeout: 3000 })).toBeTruthy();
    expect(screen.getByText(/3 waiting for the tracker\./)).toBeTruthy();
    expect(screen.getByText("The tracker hasn't answered for a while.")).toBeTruthy();
    expect(screen.getByText("Last error: Forgejo unreachable: Connection refused")).toBeTruthy();
    expect(screen.queryByRole("link", { name: "Open the tracker" })).toBeNull();
  });
});
