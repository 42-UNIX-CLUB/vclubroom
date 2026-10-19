import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { MediaView } from "./MediaView";

afterEach(cleanup);

describe("MediaView", () => {
  it("renders an image, a video player or an audio player by kind", () => {
    const { container, rerender } = render(<MediaView media={{ kind: "IMAGE", url: "/media-private/a.png" }} alt="A cat" />);
    expect(container.querySelector("img")?.getAttribute("alt")).toBe("A cat");

    rerender(<MediaView media={{ kind: "VIDEO", url: "/media-private/b.mp4" }} />);
    expect(container.querySelector("video")?.hasAttribute("controls")).toBe(true);

    rerender(<MediaView media={{ kind: "AUDIO", url: "/media-private/c.mp3" }} />);
    expect(container.querySelector("audio")?.getAttribute("src")).toBe("/media-private/c.mp3");
  });
});
