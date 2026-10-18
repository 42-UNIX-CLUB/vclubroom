import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { Markdown } from "./Markdown";

afterEach(cleanup);

describe("Markdown", () => {
  it("renders formatting and opens links safely in a new tab", () => {
    render(<Markdown>{"**Blitz** night, details on [our site](https://example.com)."}</Markdown>);

    expect(screen.getByText("Blitz").tagName).toBe("STRONG");
    const link = screen.getByRole("link", { name: "our site" });
    expect(link.getAttribute("href")).toBe("https://example.com");
    expect(link.getAttribute("target")).toBe("_blank");
    expect(link.getAttribute("rel")).toBe("noopener noreferrer nofollow");
  });

  it("never renders raw HTML or javascript: links", () => {
    const { container } = render(
      <Markdown>{'<script>alert(1)</script> <img src=x onerror="alert(1)"> [click](javascript:alert(1))'}</Markdown>,
    );

    expect(container.querySelector("script, img")).toBeNull();
    expect(container.querySelector("a")?.getAttribute("href") ?? "").not.toContain("javascript");
  });
});