import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { PostComposer } from "./PostComposer";

vi.mock("@/shared/api/client", () => ({ api: { POST: vi.fn() } }));
// The real picker uploads; here a stand-in reports one ready file, or one still uploading.
vi.mock("@/features/media", () => ({
  MediaPicker: ({ onChange }: { onChange: (ids: string[], uploading: boolean) => void }) => (
    <>
      <button type="button" onClick={() => onChange(["m1"], false)}>attach ready file</button>
      <button type="button" onClick={() => onChange([], true)}>start an upload</button>
    </>
  ),
}));

function renderComposer() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <PostComposer channelId="ch1" />
    </QueryClientProvider>,
  );
}

const postButton = () => screen.getByRole("button", { name: /Post|Uploading/ }) as HTMLButtonElement;

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("PostComposer", () => {
  it("publishes text with its attachments, then clears", async () => {
    vi.mocked(api.POST).mockResolvedValue({ data: { id: "p1" } } as never);
    renderComposer();

    expect(postButton().disabled).toBe(true);                                       // nothing to post yet
    fireEvent.change(screen.getByRole("textbox", { name: "Write a post" }), { target: { value: "Blitz tonight!" } });
    fireEvent.click(screen.getByRole("button", { name: "attach ready file" }));
    fireEvent.click(postButton());

    await waitFor(() => expect(api.POST).toHaveBeenCalledWith("/api/channels/{channelId}/posts",
      { params: { path: { channelId: "ch1" } }, body: { body: "Blitz tonight!", mediaIds: ["m1"] } }));
    await waitFor(() => expect((screen.getByRole("textbox", { name: "Write a post" }) as HTMLTextAreaElement).value).toBe(""));
  });

  it("waits for uploads before publishing", () => {
    renderComposer();
    fireEvent.change(screen.getByRole("textbox", { name: "Write a post" }), { target: { value: "Look at this" } });
    fireEvent.click(screen.getByRole("button", { name: "start an upload" }));

    expect(postButton().textContent).toBe("Uploading…");
    expect(postButton().disabled).toBe(true);
  });
});
