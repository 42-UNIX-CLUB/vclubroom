import { describe, expect, it } from "vitest";
import { emptyEventForm, eventFormSchema, eventToForm, toEditRequest, toEventRequest } from "./eventForm";

const valid = {
  spaceId: "s1", title: " Blitz night ", description: "", location: "Cluster 2", onlineUrl: "",
  startsAt: "2026-10-23T18:00", endsAt: "2026-10-23T21:00",
};

describe("eventForm", () => {
  it("wants the end after the start, and real links", () => {
    expect(eventFormSchema.safeParse(valid).success).toBe(true);
    const backwards = eventFormSchema.safeParse({ ...valid, endsAt: "2026-10-23T17:00" });
    expect(backwards.error?.issues[0]).toMatchObject({ path: ["endsAt"], message: "The event must end after it starts." });
    expect(eventFormSchema.safeParse({ ...valid, onlineUrl: "meet.example" }).success).toBe(false);
  });

  it("sends Lisbon times as instants, and blanks as nothing", () => {
    expect(toEventRequest(valid, "m1")).toEqual({
      spaceId: "s1", title: "Blitz night", location: "Cluster 2", description: undefined, onlineUrl: undefined,
      startsAt: "2026-10-23T17:00:00.000Z", endsAt: "2026-10-23T20:00:00.000Z", coverMediaId: "m1",
    });
    expect(toEditRequest(valid)).not.toHaveProperty("spaceId");
  });

  it("starts a new event tomorrow evening, and edits an event in Lisbon time", () => {
    expect(emptyEventForm("s1", new Date("2026-10-19T10:00:00Z"))).toMatchObject({
      spaceId: "s1", startsAt: "2026-10-20T18:00", endsAt: "2026-10-20T20:00",
    });
    expect(eventToForm({ spaceId: "s1", title: "Blitz", startsAt: "2026-10-23T17:00:00Z", endsAt: "2026-10-23T20:00:00Z" }))
      .toMatchObject({ startsAt: "2026-10-23T18:00", endsAt: "2026-10-23T21:00", location: "" });
  });
});
