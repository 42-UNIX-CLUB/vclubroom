import { HashIcon, MessageCircleIcon } from "lucide-react";
import type { Channel } from "./api";

/** # for a forum, a speech bubble for a chat. */
export function ChannelIcon({ type, className }: { type: Channel["type"]; className?: string }) {
  const Icon = type === "CHAT" ? MessageCircleIcon : HashIcon;
  return <Icon className={className ?? "size-4"} aria-hidden="true" />;
}
