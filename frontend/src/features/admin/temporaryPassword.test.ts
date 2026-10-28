import { describe, expect, it } from "vitest";
import { temporaryPassword } from "./temporaryPassword";

describe("temporaryPassword", () => {
  it("is long enough, random, and free of look-alike characters", () => {
    const passwords = Array.from({ length: 50 }, () => temporaryPassword());
    passwords.forEach((p) => expect(p).toMatch(/^[a-km-zA-HJ-NP-Z2-9]{14}$/));
    expect(new Set(passwords).size).toBe(50);
  });
});
