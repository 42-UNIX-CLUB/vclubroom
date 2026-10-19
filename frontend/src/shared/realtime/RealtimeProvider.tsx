import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import { useQueryClient } from "@tanstack/react-query";
import { createContext, type ReactNode, useContext, useEffect, useRef, useState } from "react";

/** What every push looks like (realtime.md §4). */
export interface RealtimeEnvelope<T = unknown> {
  type: string;
  payload: T;
  sentAt: string;
}

interface RealtimeState {
  client: Client | null;
  connected: boolean;
}

const RealtimeContext = createContext<RealtimeState>({ client: null, connected: false });

/**
 * One STOMP connection per tab, opened only for logged-in users (08-frontend §7). Pushes are best effort: after a
 * reconnect every query is refreshed, so whatever was missed during the gap shows up.
 */
export function RealtimeProvider({ enabled, children }: { enabled: boolean; children: ReactNode }) {
  const queryClient = useQueryClient();
  const [state, setState] = useState<RealtimeState>({ client: null, connected: false });
  const hasConnectedBefore = useRef(false);

  useEffect(() => {
    if (!enabled) return;
    const scheme = window.location.protocol === "https:" ? "wss" : "ws";
    const client = new Client({
      brokerURL: `${scheme}://${window.location.host}/ws`,      // same origin: the SESSION cookie goes along
      reconnectDelay: 3_000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
    });
    client.onConnect = () => {
      setState({ client, connected: true });
      if (hasConnectedBefore.current) void queryClient.invalidateQueries();
      hasConnectedBefore.current = true;
    };
    client.onWebSocketClose = () => setState({ client, connected: false });
    client.activate();
    return () => {
      setState({ client: null, connected: false });
      void client.deactivate();
    };
  }, [enabled, queryClient]);

  return <RealtimeContext.Provider value={state}>{children}</RealtimeContext.Provider>;
}

/** Subscribes while mounted (and connected); subscribes again by itself after a reconnect. */
export function useTopic<T>(destination: string | null, onMessage: (envelope: RealtimeEnvelope<T>) => void): void {
  const { client, connected } = useContext(RealtimeContext);
  const handler = useRef(onMessage);

  useEffect(() => {
    handler.current = onMessage;
  });

  useEffect(() => {
    if (!client || !connected || !destination) return;
    const subscription: StompSubscription = client.subscribe(destination, (message: IMessage) => {
      handler.current(JSON.parse(message.body) as RealtimeEnvelope<T>);
    });
    return () => subscription.unsubscribe();
  }, [client, connected, destination]);
}
