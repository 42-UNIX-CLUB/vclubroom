import { cleanup, render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it } from "vitest";
import { SiteFooter } from "./SiteFooter";

afterEach(cleanup);

describe("SiteFooter", () => {
  it("links to the API documentation and the status", () => {
    render(<MemoryRouter><SiteFooter /></MemoryRouter>);
    const nav = screen.getByRole("navigation", { name: "Platform" });
    expect(within(nav).getByRole("link", { name: "API docs" }).getAttribute("href")).toBe("/swagger-ui.html");
    expect(within(nav).getByRole("link", { name: "Status" }).getAttribute("href")).toBe("/status");
  });
});
