import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { ChatRoom, type ChatChannel } from "./ChatRoom";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn(), PATCH: vi.fn(), DELETE: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { error: vi.fn() } }));
vi.mock("@/shared/realtime/RealtimeProvider", () => ({ useTopic: () => undefined }));

const message = (id: number, body: string, authorId: string, era = 2) =>
  ({ id, body, era, clientNonce: `n${id}`, author: { id: authorId, displayName: authorId === "me" ? "Me" : "Bob" }, createdAt: "2026-10-19T17:00:00Z" });

function renderRoom(channel: ChatChannel) {
  vi.mocked(api.GET).mockImplementation(((path: string) => Promise.resolve({
    data: path === "/api/me" ? { id: "me", login: "me" }
      : { items: [message(1, "old times", "me", 1), message(2, "mine", "me"), message(3, "theirs", "bob")], nextBefore: null },
  })) as never);
  vi.mocked(api.PATCH).mockResolvedValue({ data: { ...message(2, "mine, edited", "me"), editedAt: "2026-10-19T17:05:00Z" } } as never);
  vi.mocked(api.DELETE).mockResolvedValue({ data: { ...message(3, "", "bob"), body: null, deleted: true } } as never);
  const router = createMemoryRouter([{ path: "/lounge", element: <ChatRoom channel={channel} /> }], { initialEntries: ["/lounge"] });
  render(<QueryClientProvider client={new QueryClient()}><RouterProvider router={router} /></QueryClientProvider>);
}

const item = async (text: string) => (await screen.findByText(text, {}, { timeout: 3000 })).closest("li") as HTMLElement;

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("Chat message actions", () => {
  it("I edit my own messages of this era, and nobody else's", async () => {
    renderRoom({ id: "ch2", canWrite: true, currentEra: 2 });

    await waitFor(async () => expect(within(await item("mine")).queryByRole("button", { name: "Edit" })).toBeTruthy());
    expect(within(await item("theirs")).queryByRole("button", { name: "Edit" })).toBeNull();
    expect(within(await item("old times")).queryByRole("button", { name: "Edit" })).toBeNull();     // an earlier era

    fireEvent.click(within(await item("mine")).getByRole("button", { name: "Edit" }));
    fireEvent.change(screen.getByRole("textbox", { name: "Edit your message" }), { target: { value: "mine, edited" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => expect(api.PATCH).toHaveBeenCalledWith("/api/messages/{messageId}", { params: { path: { messageId: 2 } }, body: { body: "mine, edited" } }));
    expect(await screen.findByText("mine, edited")).toBeTruthy();
  });

  it("moderators delete anyone's message after confirming", async () => {
    renderRoom({ id: "ch2", canWrite: true, canModerate: true, currentEra: 2 });

    await waitFor(async () => expect(within(await item("theirs")).queryByRole("button", { name: "Delete" })).toBeTruthy());
    expect(within(await item("theirs")).queryByRole("button", { name: "Edit" })).toBeNull();
    fireEvent.click(within(await item("theirs")).getByRole("button", { name: "Delete" }));
    fireEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Delete message" }));
    await waitFor(() => expect(api.DELETE).toHaveBeenCalledWith("/api/messages/{messageId}", { params: { path: { messageId: 3 } } }));
    expect(await screen.findByText("Message deleted.")).toBeTruthy();
  });
});
