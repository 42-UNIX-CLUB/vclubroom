import { Badge } from "./badge";

/** Club lifecycle status as stored by the backend (club.clubs.status). */
export type ClubStatus = "PENDING_APPROVAL" | "ACTIVE" | "REJECTED" | "ARCHIVED";

const LOOK: Record<ClubStatus, { label: string; variant: "default" | "secondary" | "outline" | "destructive" }> = {
  ACTIVE: { label: "Active", variant: "default" },
  ARCHIVED: { label: "Archived", variant: "secondary" },
  PENDING_APPROVAL: { label: "Pending approval", variant: "outline" },
  REJECTED: { label: "Rejected", variant: "destructive" },
};

export function StatusBadge({ status }: { status: ClubStatus }) {
  const { label, variant } = LOOK[status];
  return <Badge variant={variant}>{label}</Badge>;
}