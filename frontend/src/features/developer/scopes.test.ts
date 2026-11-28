import { describe, expect, it } from "vitest";
import { describeScopes, expiresAt } from "./scopes";

describe("token scopes and expiry", () => {
  const now = new Date("2026-10-30T12:00:00Z");

  it("expires after a number of days, at the end of a chosen day, or never", () => {
    expect(expiresAt("30", "", now)).toBe("2026-11-29T12:00:00.000Z");
    expect(expiresAt("365", "", now)).toBe("2027-10-30T12:00:00.000Z");
    expect(new Date(expiresAt("date", "2026-12-24", now) ?? "").getDate()).toBe(24);
    expect(expiresAt("never", "", now)).toBeNull();
  });

  it("describes scopes by area, in the areas' order", () => {
    expect(describeScopes(["forum:write", "profile:read", "forum:read"])).toBe("Profile: read · Forum: read, write");
    expect(describeScopes([])).toBe("");
  });
});
