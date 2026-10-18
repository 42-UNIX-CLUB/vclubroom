import { describe, expect, it } from "vitest";
import { clubFormSchema, emptyClubForm, parseTags, toCreateClubRequest } from "./clubForm";

describe("parseTags", () => {
  it("normalises what people type", () => {
    expect(parseTags("Chess, #board-games  chess,,")).toEqual(["chess", "board-games"]);
    expect(parseTags("   ")).toEqual([]);
  });
});

describe("clubFormSchema", () => {
  const valid = { ...emptyClubForm, name: "Chess Club", category: "Games", tags: "chess beginners" };

  it("accepts a minimal club", () => {
    expect(clubFormSchema.safeParse(valid).success).toBe(true);
  });

  it.each([
    [{ name: "Go" }, "name", "Use at least 3 characters."],
    [{ category: "" }, "category", "Choose a category."],
    [{ tags: "ok no_underscores" }, "tags", "Tags are 2–24 lowercase letters, digits or hyphens."],
    [{ tags: Array.from({ length: 11 }, (_, i) => `tag${i}`).join(" ") }, "tags", "Use at most 10 tags."],
  ])("rejects %j", (change, field, message) => {
    const result = clubFormSchema.safeParse({ ...valid, ...change });
    expect(result.success).toBe(false);
    expect(result.error?.issues.find((issue) => issue.path[0] === field)?.message).toBe(message);
  });

  it("builds the API request, leaving out empty optional fields", () => {
    expect(toCreateClubRequest({ ...valid, name: "  Chess Club ", tagline: "  " })).toEqual({
      name: "Chess Club", tagline: undefined, description: undefined, category: "Games", tags: ["chess", "beginners"],
    });
  });
});