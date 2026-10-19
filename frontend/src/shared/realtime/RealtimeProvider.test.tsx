import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { RealtimeProvider, useTopic } from "./RealtimeProvider";

/** A stand-in for @stomp/stompjs: records the client and lets the test play the server. */
const stomp = vi.hoisted(() => ({
  clients: [] as {
    brokerURL: string;
    onConnect?: () => void;
    onWebSocketClose?: () => void;
    subscriptions: Map<string, (message: { body: string }) => void>;
    activate: ReturnType<typeof vi.fn>;
    deactivate: ReturnType<typeof vi.fn>;
  }[],
}));
vi.mock("@stomp/stompjs", () => ({
  Client: class {
    brokerURL: string;
    onConnect?: () => void;
    onWebSocketClose?: () => void;
    subscriptions = new Map<string, (message: { body: string }) => void>();
    activate = vi.fn();
    deactivate = vi.fn();
    constructor(config: { brokerURL: string }) {
      this.brokerURL = config.brokerURL;
      stomp.clients.push(this);
    }
    subscribe(destination: string, callback: (message: { body: string }) => void) {
      this.subscriptions.set(destination, callback);
      return { unsubscribe: () => this.subscriptions.delete(destination) };
    }
  },
}));

function Listener({ onMessage }: { onMessage: (type: string) => void }) {
  useTopic<unknown>("/topic/channels/c1/posts", (envelope) => onMessage(envelope.type));
  return null;
}

afterEach(() => {
  cleanup();
  stomp.clients.length = 0;
});

describe("RealtimeProvider", () => {
  it("connects for logged-in users, subscribes once connected, and hands over the envelopes", () => {
    const received = vi.fn();
    render(<QueryClientProvider client={new QueryClient()}><RealtimeProvider enabled><Listener onMessage={received} /></RealtimeProvider></QueryClientProvider>);

    const client = stomp.clients[0]!;
    expect(client.brokerURL).toBe("ws://localhost:3000/ws");
    expect(client.activate).toHaveBeenCalled();
    expect(client.subscriptions.size).toBe(0);                          // not before the connection is up
    act(() => client.onConnect?.());
    act(() => client.subscriptions.get("/topic/channels/c1/posts")?.({ body: '{"type":"post.created","payload":{},"sentAt":"x"}' }));
    expect(received).toHaveBeenCalledWith("post.created");
  });

  it("refreshes every query after a reconnect, not after the first connection", () => {
    const queryClient = new QueryClient();
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");
    render(<QueryClientProvider client={queryClient}><RealtimeProvider enabled><Listener onMessage={() => {}} /></RealtimeProvider></QueryClientProvider>);

    const client = stomp.clients[0]!;
    act(() => client.onConnect?.());
    expect(invalidate).not.toHaveBeenCalled();
    act(() => client.onWebSocketClose?.());
    act(() => client.onConnect?.());
    expect(invalidate).toHaveBeenCalledTimes(1);
  });

  it("stays offline for visitors", () => {
    render(<QueryClientProvider client={new QueryClient()}><RealtimeProvider enabled={false}><Listener onMessage={() => {}} /></RealtimeProvider></QueryClientProvider>);
    expect(stomp.clients).toHaveLength(0);
  });
});
