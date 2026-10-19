import { CrownIcon } from "lucide-react";
import { Link } from "react-router";
import { toast } from "sonner";
import { formatDate } from "@/shared/lib/dates";
import { Button } from "@/shared/ui/button";
import { type OwnershipInvitation, useAnswerInvitation, useMyInvitations } from "./api";

/**
 * My pending ownership invitations, above every page until answered (the inbox of E13-S01).
 * E12 also sends a notification; this stays the place to answer.
 */
export function OwnershipInvitations() {
  const { data: invitations = [] } = useMyInvitations();
  // One mutation for the list: it stays mounted while the answered invitation disappears, so the toast still runs.
  const answer = useAnswerInvitation();
  if (invitations.length === 0) return null;

  function respond(invitation: OwnershipInvitation, accept: boolean) {
    answer.mutate({ invitation, accept }, {
      onSuccess: () => toast.success(accept ? `You are now an owner of ${invitation.clubName}.` : "Invitation declined."),
      onError: (error) => toast.error(error.message),          // e.g. INVITATION_EXPIRED
    });
  }

  return (
    <ul aria-label="Ownership invitations" className="mb-6 space-y-2">
      {invitations.map((invitation) => (
        <li key={invitation.id} className="flex flex-wrap items-center gap-3 rounded-xl border border-primary/30 bg-primary/5 p-3 text-sm">
          <CrownIcon className="size-4 shrink-0 text-primary" aria-hidden="true" />
          <p className="min-w-0 flex-1">
            <span className="font-medium">{invitation.invitedBy?.displayName ?? "An owner"}</span>
            {" invites you to become an owner of "}
            <Link to={`/c/${invitation.clubSlug}`} className="font-medium underline-offset-4 hover:underline">{invitation.clubName}</Link>
            {invitation.transfer && " (they step down when you accept)"}
            {invitation.expiresAt && <span className="text-muted-foreground"> · until {formatDate(invitation.expiresAt)}</span>}
          </p>
          <div className="flex gap-2">
            <Button size="sm" disabled={answer.isPending} onClick={() => respond(invitation, true)}>Accept</Button>
            <Button size="sm" variant="ghost" disabled={answer.isPending} onClick={() => respond(invitation, false)}>Decline</Button>
          </div>
        </li>
      ))}
    </ul>
  );
}
