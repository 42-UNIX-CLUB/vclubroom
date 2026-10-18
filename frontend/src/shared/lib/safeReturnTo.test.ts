import { describe, expect, it } from "vitest";
import { safeReturnTo } from "./safeReturnTo";

describe("safeReturnTo", () => {
  it("keeps same-app paths, including query strings", () => {
    expect(safeReturnTo("/clubs/new")).toBe("/clubs/new");
    expect(safeReturnTo("/c/chess?tab=events")).toBe("/c/chess?tab=events");
  });

  it.each([null, "", "https://evil.example", "//evil.example", "/\\evil.example", "javascript:alert(1)", "clubs"])(
    "falls back to / for %j",
    (value) => expect(safeReturnTo(value)).toBe("/"),
  );
});
