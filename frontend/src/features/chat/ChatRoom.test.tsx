import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { ChatRoom, type ChatChannel } from "./ChatRoom";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { error: vi.fn() } }));
const live = vi.hoisted(() => ({ handlers: new Map<string, (envelope: { type: string; payload: unknown }) => void>() }));
vi.mock("@/shared/realtime/RealtimeProvider", () => ({
  useTopic: (destination: string | null, handler: (envelope: { type: string; payload: unknown }) => void) => {
    if (destination) live.handlers.set(destination, handler);
  },
}));

const message = (id: number, body: string, clientNonce = `n${id}`) => ({ id, body, clientNonce, author: { displayName: "Ada" }, createdAt: "2026-10-19T17:00:00Z" });

/** Two pages: the newest (no cursor) has messages 2 and 3, the older one message 1. */
function backend() {
  vi.mocked(api.GET).mockImplementation(((_path: string, init: { params: { query: { before?: number } } }) =>
    Promise.resolve({ data: init.params.query.before
      ? { items: [message(1, "first")], nextBefore: null }
      : { items: [message(2, "second"), message(3, "third")], nextBefore: 2 } })) as never);
}

function renderRoom(channel: ChatChannel = { id: "ch2", canWrite: true, currentEra: 1 }) {
  const router = createMemoryRouter([{ path: "/lounge", element: <ChatRoom channel={channel} /> }], { initialEntries: ["/lounge"] });
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><RouterProvider router={router} /></QueryClientProvider>);
}

const bodies = () => within(screen.getByRole("list", { name: "Messages" })).getAllByRole("listitem").map((li) => li.querySelector("p:last-child")?.textContent);
const push = (type: string, payload: unknown) => act(() => live.handlers.get("/topic/channels/ch2/messages")?.({ type, payload }));
const write = (text: string) => {
  fireEvent.change(screen.getByRole("textbox", { name: "Write a message" }), { target: { value: text } });
  fireEvent.keyDown(screen.getByRole("textbox", { name: "Write a message" }), { key: "Enter" });
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("ChatRoom", () => {
  it("shows the history oldest first and loads older messages above", async () => {
    backend();
    renderRoom();

    await waitFor(() => expect(bodies()).toEqual(["second", "third"]));
    fireEvent.click(screen.getByRole("button", { name: "Load older messages" }));
    await waitFor(() => expect(bodies()).toEqual(["first", "second", "third"]));
  });

  it("shows my message at once as sending, then once only, whether the answer or the push comes first", async () => {
    backend();
    let answer!: (value: unknown) => void;
    vi.mocked(api.POST).mockReturnValue(new Promise((resolve) => { answer = resolve; }) as never);
    renderRoom();
    await waitFor(() => expect(bodies()).toEqual(["second", "third"]));

    write("hello");
    expect(await screen.findByText("Sending…")).toBeTruthy();
    const sent = vi.mocked(api.POST).mock.calls[0]?.[1] as { body: { body: string; clientNonce: string } };
    expect(sent.body.body).toBe("hello");
    push("message.created", message(4, "hello", sent.body.clientNonce));                 // the push wins the race
    await waitFor(() => expect(screen.queryByText("Sending…")).toBeNull());
    await act(async () => answer({ data: message(4, "hello", sent.body.clientNonce) }));
    expect(bodies()).toEqual(["second", "third", "hello"]);
  });

  it("appends other people's messages and shows deletions", async () => {
    backend();
    renderRoom();
    await waitFor(() => expect(bodies()).toEqual(["second", "third"]));

    push("message.created", message(5, "from Bob"));
    push("message.created", message(5, "from Bob"));                                     // a repeat changes nothing
    await waitFor(() => expect(bodies()).toEqual(["second", "third", "from Bob"]));
    push("message.deleted", { ...message(3, ""), body: null, deleted: true });
    await waitFor(() => expect(screen.getByText("Message deleted.")).toBeTruthy());
  });

  it("offers a retry with the same nonce when sending fails", async () => {
    backend();
    vi.mocked(api.POST).mockRejectedValueOnce(new Error("Network down")).mockResolvedValueOnce({ data: message(6, "again?") } as never);
    renderRoom();
    await waitFor(() => expect(bodies()).toEqual(["second", "third"]));

    write("again?");
    fireEvent.click(await screen.findByRole("button", { name: "Retry" }));
    await waitFor(() => expect(api.POST).toHaveBeenCalledTimes(2));
    const nonces = vi.mocked(api.POST).mock.calls.map((call) => (call[1] as { body: { clientNonce: string } }).body.clientNonce);
    expect(nonces[1]).toBe(nonces[0]);
  });

  it("only reads when the viewer cannot write", async () => {
    backend();
    renderRoom({ id: "ch2", canWrite: false, currentEra: 1 });
    await waitFor(() => expect(bodies()).toEqual(["second", "third"]));
    expect(screen.queryByRole("textbox", { name: "Write a message" })).toBeNull();
  });
});
