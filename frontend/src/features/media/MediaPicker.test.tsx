import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MediaPicker } from "./MediaPicker";
import { uploadMedia } from "./uploadMedia";

vi.mock("./uploadMedia", () => ({ uploadMedia: vi.fn() }));

/** An upload that the test advances by hand: progress(p), done(id), fail(message). */
function controllableUpload() {
  const control: { progress: (p: number) => void; done: (id: string) => void; fail: (m: string) => void } = {} as never;
  vi.mocked(uploadMedia).mockImplementationOnce((_file, _purpose, onProgress) => new Promise((resolve, reject) => {
    control.progress = (p) => onProgress?.(p);
    control.done = resolve;
    control.fail = (m) => reject(new Error(m));
  }));
  return control;
}

const choose = (...files: File[]) => fireEvent.change(screen.getByLabelText("Choose files"), { target: { files } });

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("MediaPicker", () => {
  it("shows each upload's progress and reports the files that are ready", async () => {
    const upload = controllableUpload();
    const onChange = vi.fn();
    render(<MediaPicker purpose="POST_ATTACHMENT" onChange={onChange} />);

    choose(new File(["v"], "clip.mp4", { type: "video/mp4" }));
    await waitFor(() => expect(onChange).toHaveBeenLastCalledWith([], true));    // uploading
    act(() => upload.progress(40));
    expect(screen.getByRole("progressbar", { name: "Uploading clip.mp4" }).getAttribute("aria-valuenow")).toBe("40");

    await act(async () => upload.done("m1"));
    expect(screen.getByText("Uploaded")).toBeTruthy();
    expect(onChange).toHaveBeenLastCalledWith(["m1"], false);
  });

  it("refuses files that are too large or of the wrong kind before uploading", () => {
    render(<MediaPicker purpose="CLUB_AVATAR" onChange={vi.fn()} />);

    choose(new File(["v"], "clip.mp4", { type: "video/mp4" }));
    expect(screen.getByRole("alert").textContent).toBe("Only images are allowed here.");
    const huge = new File(["x"], "huge.png", { type: "image/png" });
    Object.defineProperty(huge, "size", { value: 11 * 1024 * 1024 });
    choose(huge);
    expect(screen.getAllByRole("alert")[1]?.textContent).toBe("Too large: images can be up to 10 MB.");
    expect(uploadMedia).not.toHaveBeenCalled();
  });

  it("shows the server's reason when an upload fails, and removes files", async () => {
    const upload = controllableUpload();
    const onChange = vi.fn();
    render(<MediaPicker purpose="POST_ATTACHMENT" onChange={onChange} />);

    choose(new File(["a"], "song.flac", { type: "audio/flac" }));
    await act(async () => upload.fail("Unsupported file type audio/flac"));
    expect(screen.getByRole("alert").textContent).toBe("Unsupported file type audio/flac");

    fireEvent.click(screen.getByRole("button", { name: "Remove song.flac" }));
    expect(screen.queryByText(/song.flac/)).toBeNull();
    expect(onChange).toHaveBeenLastCalledWith([], false);
  });
});
