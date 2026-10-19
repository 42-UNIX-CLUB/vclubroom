import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { uploadMedia } from "./uploadMedia";

vi.mock("@/shared/api/client", () => ({ api: { POST: vi.fn() } }));

/** A stand-in for the browser's XMLHttpRequest that records what it was asked to do. */
class FakeXhr {
  static last: FakeXhr;
  static status = 200;
  method = "";
  url = "";
  headers: Record<string, string> = {};
  body: unknown;
  status = 0;
  upload: { onprogress?: (e: { lengthComputable: boolean; loaded: number; total: number }) => void } = {};
  onload?: () => void;
  onerror?: () => void;
  constructor() { FakeXhr.last = this; }
  open(method: string, url: string) { this.method = method; this.url = url; }
  setRequestHeader(name: string, value: string) { this.headers[name] = value; }
  send(body: unknown) {
    this.body = body;
    this.upload.onprogress?.({ lengthComputable: true, loaded: 50, total: 100 });
    this.upload.onprogress?.({ lengthComputable: true, loaded: 100, total: 100 });
    this.status = FakeXhr.status;
    this.onload?.();
  }
}

const slot = { mediaId: "m1", uploadUrl: "http://localhost:5173/media-private/post_attachment/2026/10/m1?X-Amz-Signature=x",
  method: "PUT", headers: { host: ["localhost:5173"], "content-type": ["image/png"] } };

afterEach(() => {
  vi.clearAllMocks();
  vi.unstubAllGlobals();
  FakeXhr.status = 200;
});

describe("uploadMedia", () => {
  it("asks for a slot, PUTs the file with the signed headers, reports progress, then completes", async () => {
    vi.stubGlobal("XMLHttpRequest", FakeXhr);
    vi.mocked(api.POST).mockResolvedValueOnce({ data: slot } as never).mockResolvedValueOnce({ data: {} } as never);
    const file = new File(["png bytes"], "cat.png", { type: "image/png" });
    const progress: number[] = [];

    await expect(uploadMedia(file, "POST_ATTACHMENT", (p) => progress.push(p))).resolves.toBe("m1");

    expect(vi.mocked(api.POST).mock.calls[0]).toEqual(["/api/media/uploads",
      { body: { purpose: "POST_ATTACHMENT", fileName: "cat.png", contentType: "image/png", sizeBytes: 9 } }]);
    expect(FakeXhr.last.method).toBe("PUT");
    expect(FakeXhr.last.url).toBe(slot.uploadUrl);
    expect(FakeXhr.last.headers).toEqual({ "content-type": "image/png" });        // not Host: the browser sets it
    expect(FakeXhr.last.body).toBe(file);
    expect(progress).toEqual([50, 100]);
    expect(vi.mocked(api.POST).mock.calls[1]).toEqual(["/api/media/{mediaId}/complete", { params: { path: { mediaId: "m1" } } }]);
  });

  it("stops when storage refuses the upload", async () => {
    vi.stubGlobal("XMLHttpRequest", FakeXhr);
    FakeXhr.status = 403;
    vi.mocked(api.POST).mockResolvedValueOnce({ data: slot } as never);

    await expect(uploadMedia(new File(["x"], "cat.png", { type: "image/png" }), "POST_ATTACHMENT")).rejects.toThrow("Upload failed (403)");
    expect(api.POST).toHaveBeenCalledTimes(1);                                   // no completion
  });
});
