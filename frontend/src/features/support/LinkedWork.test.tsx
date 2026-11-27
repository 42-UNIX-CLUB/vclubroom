import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { LinkedWork, progress } from "./LinkedWork";

afterEach(cleanup);

describe("LinkedWork", () => {
  it("says how far each piece of work is, and in which release", () => {
    expect(progress({ state: "PLANNED" })).toBe("Planned");
    expect(progress({ state: "PLANNED", milestone: "v1.2.0" })).toBe("Planned · planned for v1.2.0");
    expect(progress({ state: "IN_PROGRESS", milestone: "v1.2.0" })).toBe("In progress · planned for v1.2.0");
    expect(progress({ state: "DONE", milestone: "v1.2.0", released: false })).toBe("Done · coming in v1.2.0");
    expect(progress({ state: "DONE", milestone: "v1.2.0", released: true })).toBe("Released in v1.2.0");
    expect(progress({ state: "DONE" })).toBe("Done");
  });

  it("lists the work with its reference and title", () => {
    render(<LinkedWork items={[
      { ref: "E09-S04", title: "Edit, delete, pin and moderate", state: "IN_PROGRESS", milestone: "v1.2.0" },
      { ref: "#301", title: "Pinned posts disappear after a reload", state: "DONE", milestone: "v1.1.0", released: true },
    ]} />);

    const items = within(screen.getByRole("region", { name: "Linked work" })).getAllByRole("listitem");
    expect(items[0].textContent).toBe("E09-S04 · Edit, delete, pin and moderateIn progress · planned for v1.2.0");
    expect(items[1].textContent).toBe("#301 · Pinned posts disappear after a reloadReleased in v1.1.0");
  });
});
