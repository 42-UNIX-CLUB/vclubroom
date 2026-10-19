import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { PlaceholderPage } from "./PlaceholderPage";

afterEach(cleanup);

describe("PlaceholderPage", () => {
  it("tells users the page is being built; the backlog story only shows in development", () => {
    render(<PlaceholderPage title="Members" story="E07-S04" />);

    expect(screen.getByRole("heading", { name: "Members" })).toBeTruthy();
    expect(screen.getByText("This part of Virtual Clubroom is still being built.")).toBeTruthy();
    expect(screen.getByText("Backlog: E07-S04")).toBeTruthy();                // vitest runs in development mode
  });
});
