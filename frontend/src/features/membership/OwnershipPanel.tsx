import { CrownIcon, LockIcon } from "lucide-react";
import { useState } from "react";
import { useNavigate, useOutletContext } from "react-router";
import { toast } from "sonner";
import { useMe } from "@/features/auth";
import { formatDate } from "@/shared/lib/dates";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/ui/card";
import { ClubAvatar } from "@/shared/ui/ClubAvatar";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/shared/ui/dialog";
import { EmptyState } from "@/shared/ui/EmptyState";
import { FullPageSpinner } from "@/shared/ui/FullPageSpinner";
import {
  type UserSummary, useClubInvitations, useInviteOwner, useMemberSummary, useMyMembership, useRevokeInvitation, useStepDown,
} from "./api";
import { UserPicker } from "./UserPicker";

/**
 * Club settings › Ownership (owners only, 08-frontend §9.3): the owners, inviting a co-owner or transferring
 * ownership, the invitations still waiting, and stepping down. Reads the club from the outlet context, like
 * JoinRequestsPage, to avoid an import cycle with the clubs feature.
 */
export function OwnershipPanel() {
  const club = useOutletContext<{ id?: string; slug?: string; name?: string }>();
  const clubId = club.id ?? "";
  const { data: membership } = useMyMembership(clubId);
  const { data: summary } = useMemberSummary(clubId);

  if (!membership || !summary) return <FullPageSpinner />;
  if (!membership.owner) {
    return <EmptyState icon={LockIcon} title="Only owners manage ownership" />;
  }
  const owners = summary.owners ?? [];
  return (
    <section className="space-y-4">
      <Owners owners={owners} />
      <InviteOwner clubId={clubId} />
      <PendingInvitations clubId={clubId} />
      <StepDown club={{ id: clubId, slug: club.slug ?? "", name: club.name ?? "" }} lastOwner={owners.length <= 1} />
    </section>
  );
}

function Owners({ owners }: { owners: UserSummary[] }) {
  const { data: me } = useMe();
  return (
    <Card>
      <CardHeader>
        <CardTitle>Owners</CardTitle>
        <CardDescription>Owners hold every permission in the club. A club always keeps at least one.</CardDescription>
      </CardHeader>
      <CardContent>
        <ul className="space-y-2">
          {owners.map((owner) => (
            <li key={owner.id} className="flex items-center gap-3 text-sm">
              <ClubAvatar name={owner.displayName ?? ""} url={owner.avatarUrl} size="sm" />
              <span className="font-medium">{owner.displayName}</span>
              <span className="text-muted-foreground">@{owner.login}</span>
              {owner.id === me?.id && <Badge variant="secondary">You</Badge>}
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}

function InviteOwner({ clubId }: { clubId: string }) {
  const invite = useInviteOwner(clubId);
  const [invitee, setInvitee] = useState<UserSummary | null>(null);

  function send(transfer: boolean) {
    if (!invitee?.id) return;
    invite.mutate({ inviteeId: invitee.id, transfer }, {
      onSuccess: () => {
        toast.success(`Invitation sent to ${invitee.displayName}.`);
        setInvitee(null);
      },
      onError: (error) => toast.error(error.message),          // e.g. INVITEE_NOT_MEMBER, INVITATION_PENDING
    });
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Invite an owner</CardTitle>
        <CardDescription>
          Only members can become owners. The invitation waits 7 days for their answer.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <UserPicker value={invitee} onChange={setInvitee} label="Search members" clubId={clubId} />
        <div className="flex flex-wrap gap-2">
          <Button disabled={!invitee || invite.isPending} onClick={() => send(false)}>Invite as co-owner</Button>
          <Button variant="outline" disabled={!invitee || invite.isPending} onClick={() => send(true)}>Transfer ownership</Button>
        </div>
        <p className="text-xs text-muted-foreground">
          Transfer: they become an owner when they accept, and you step down at the same moment.
        </p>
      </CardContent>
    </Card>
  );
}

function PendingInvitations({ clubId }: { clubId: string }) {
  const { data: invitations = [] } = useClubInvitations(clubId);
  // One mutation for the list: it stays mounted while the revoked row disappears, so the toast still runs.
  const revoke = useRevokeInvitation(clubId);
  if (invitations.length === 0) return null;
  return (
    <Card>
      <CardHeader>
        <CardTitle>Waiting for an answer</CardTitle>
      </CardHeader>
      <CardContent>
        <ul className="space-y-2">
          {invitations.map((invitation) => (
            <li key={invitation.id} className="flex flex-wrap items-center gap-3 text-sm">
              <span className="font-medium">{invitation.invitee?.displayName}</span>
              {invitation.transfer && <Badge variant="outline">Transfer</Badge>}
              {invitation.expiresAt && <span className="text-muted-foreground">until {formatDate(invitation.expiresAt)}</span>}
              <Button
                variant="ghost"
                size="sm"
                className="ml-auto"
                disabled={revoke.isPending}
                onClick={() => revoke.mutate(invitation.id ?? "", {
                  onSuccess: () => toast.success("Invitation revoked."),
                  onError: (error) => toast.error(error.message),
                })}
              >
                Revoke
              </Button>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}

function StepDown({ club, lastOwner }: { club: { id: string; slug: string; name: string }; lastOwner: boolean }) {
  const stepDown = useStepDown(club.id);
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);

  function confirm() {
    stepDown.mutate(undefined, {
      onSuccess: () => {
        toast.success(`You are no longer an owner of ${club.name}.`);
        void navigate(`/c/${club.slug}`);
      },
      onError: (error) => {
        toast.error(error.message);                        // LAST_OWNER if the other owner stepped down first
        setOpen(false);
      },
    });
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Step down</CardTitle>
        <CardDescription>
          {lastOwner
            ? "You are the only owner. Invite a co-owner or transfer ownership first: a club cannot be left without an owner."
            : "You stay a member, without the owner's permissions."}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Button variant="destructive" disabled={lastOwner} onClick={() => setOpen(true)}>
          <CrownIcon /> Step down as owner
        </Button>
      </CardContent>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Step down as owner of {club.name}?</DialogTitle>
            <DialogDescription>To become an owner again, another owner has to invite you.</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setOpen(false)}>Cancel</Button>
            <Button variant="destructive" disabled={stepDown.isPending} onClick={confirm}>Step down</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  );
}
