import { describe, expect, it } from "vitest";
import { formatDate, formatDateTime, formatRelative, formatTime, fromLisbonInput, toLisbonInput } from "./dates";

describe("dates (Europe/Lisbon)", () => {
  it("shows Lisbon summer time (UTC+1) before the clocks change on 25 Oct 2026", () => {
    expect(formatDateTime("2026-10-19T09:30:00Z")).toBe("19 Oct 2026, 10:30");
    expect(formatDate("2026-10-19T23:30:00Z")).toBe("20 Oct 2026");   // already the next day in Lisbon
  });

  it("shows Lisbon winter time (UTC+0) after the change", () => {
    expect(formatTime("2026-11-02T09:30:00Z")).toBe("09:30");
  });

  it("formats relative times", () => {
    const now = new Date("2026-10-19T12:00:00Z");
    expect(formatRelative("2026-10-19T11:59:50Z", now)).toBe("now");
    expect(formatRelative("2026-10-19T11:55:00Z", now)).toBe("5 minutes ago");
    expect(formatRelative("2026-10-18T12:00:00Z", now)).toBe("yesterday");
    expect(formatRelative("2026-10-19T14:00:00Z", now)).toBe("in 2 hours");
  });

  it("reads and writes datetime inputs in Lisbon time, whatever the browser's zone", () => {
    expect(toLisbonInput("2026-10-23T17:00:00Z")).toBe("2026-10-23T18:00");
    expect(fromLisbonInput("2026-10-23T18:00")).toBe("2026-10-23T17:00:00.000Z");      // summer: UTC+1
    expect(fromLisbonInput("2026-12-01T18:00")).toBe("2026-12-01T18:00:00.000Z");      // winter: UTC+0
    expect(fromLisbonInput("2026-10-25T03:00")).toBe("2026-10-25T03:00:00.000Z");      // just after the change
  });
});
