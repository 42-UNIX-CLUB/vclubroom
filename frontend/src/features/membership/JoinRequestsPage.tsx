import { InboxIcon, LockIcon } from "lucide-react";
import { useOutletContext } from "react-router";
import { toast } from "sonner";
import { formatRelative } from "@/shared/lib/dates";
import { Button } from "@/shared/ui/button";
import { Card, CardContent } from "@/shared/ui/card";
import { ClubAvatar } from "@/shared/ui/ClubAvatar";
import { EmptyState } from "@/shared/ui/EmptyState";
import { FullPageSpinner } from "@/shared/ui/FullPageSpinner";
import { type JoinRequest, useDecideJoinRequest, useJoinRequests, useMyMembership } from "./api";
import { can } from "./can";

/**
 * Club settings › Join requests (MEMBER_APPROVE). The club frame passes the club down as outlet context; this page
 * reads it directly, because importing useCurrentClub from the clubs feature would create an import cycle
 * (clubs already imports membership for the join button).
 */
export function JoinRequestsPage() {
  const club = useOutletContext<{ id?: string }>();
  const clubId = club.id ?? "";
  const { data: membership } = useMyMembership(clubId);
  const allowed = can(membership, "MEMBER_APPROVE");
  const { data: requests } = useJoinRequests(clubId, allowed);
  // One mutation for the whole page: it stays mounted while the decided card disappears, so the toasts still run.
  const decide = useDecideJoinRequest(clubId);

  if (!membership) return <FullPageSpinner />;
  if (!allowed) {
    return <EmptyState icon={LockIcon} title="Only approvers see join requests" description="Ask an owner for a role with the Approve members permission." />;
  }
  if (!requests) return <FullPageSpinner />;

  function onDecide(request: JoinRequest, approve: boolean) {
    const name = request.requester?.displayName ?? "This student";
    decide.mutate({ requestId: request.id ?? "", approve }, {
      onSuccess: () => toast.success(approve ? `${name} is now a member.` : `Request from ${name} declined.`),
      onError: (error) => toast.error(error.message),
    });
  }

  return (
    <section className="space-y-4">
      <div>
        <h2 className="text-lg font-semibold">Join requests</h2>
        <p className="text-sm text-muted-foreground">Approving makes someone a member at once; declining closes their request.</p>
      </div>
      {requests.length === 0
        ? <EmptyState icon={InboxIcon} title="No pending requests" description="New requests show up here, oldest first." />
        : (
          <ul className="space-y-3">
            {requests.map((request) => (
              <li key={request.id}>
                <RequestCard request={request} busy={decide.isPending} onDecide={(approve) => onDecide(request, approve)} />
              </li>
            ))}
          </ul>
        )}
    </section>
  );
}

function RequestCard({ request, busy, onDecide }: { request: JoinRequest; busy: boolean; onDecide: (approve: boolean) => void }) {
  const name = request.requester?.displayName ?? "Unknown user";
  return (
    <Card size="sm">
      <CardContent className="flex flex-wrap items-start gap-4">
        <ClubAvatar name={name} url={request.requester?.avatarUrl} />
        <div className="min-w-0 flex-1 space-y-1">
          <p className="text-sm">
            <span className="font-medium">{name}</span>
            {request.requester?.login && <span className="text-muted-foreground"> @{request.requester.login}</span>}
          </p>
          {request.createdAt && <p className="text-xs text-muted-foreground">Asked {formatRelative(request.createdAt)}</p>}
          {request.message && <blockquote className="border-l-2 pl-3 text-sm text-muted-foreground">{request.message}</blockquote>}
        </div>
        <div className="flex gap-2">
          <Button variant="outline" disabled={busy} onClick={() => onDecide(false)}>Decline</Button>
          <Button disabled={busy} onClick={() => onDecide(true)}>Approve</Button>
        </div>
      </CardContent>
    </Card>
  );
}
