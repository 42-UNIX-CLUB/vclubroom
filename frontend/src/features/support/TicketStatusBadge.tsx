import { Badge } from "@/shared/ui/badge";
import type { TicketStatus } from "./api";

const LOOK: Record<TicketStatus, { label: string; variant: "default" | "secondary" | "outline" | "destructive" }> = {
  RECEIVED: { label: "Received", variant: "outline" },
  OPEN: { label: "Open", variant: "secondary" },
  IN_PROGRESS: { label: "In progress", variant: "default" },
  WAITING_ON_REPORTER: { label: "Waiting for you", variant: "destructive" },
  RESOLVED: { label: "Resolved", variant: "secondary" },
  DECLINED: { label: "Won't do", variant: "outline" },
  DUPLICATE: { label: "Duplicate", variant: "outline" },
};

/** What the team says about a ticket, in the reporter's words. */
export function TicketStatusBadge({ status }: { status: TicketStatus }) {
  const { label, variant } = LOOK[status];
  return <Badge variant={variant}>{label}</Badge>;
}
