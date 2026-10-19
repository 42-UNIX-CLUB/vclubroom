import { LockIcon, UsersIcon } from "lucide-react";
import { useState } from "react";
import { useOutletContext } from "react-router";
import { toast } from "sonner";
import { ApiError } from "@/shared/api/errors";
import { formatDate } from "@/shared/lib/dates";
import { useDebounce } from "@/shared/lib/useDebounce";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { ClubAvatar } from "@/shared/ui/ClubAvatar";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/shared/ui/dialog";
import { EmptyState } from "@/shared/ui/EmptyState";
import { Input } from "@/shared/ui/input";
import { PageHeader } from "@/shared/ui/PageHeader";
import { Skeleton } from "@/shared/ui/skeleton";
import { type Member, useMembers, useMyMembership, useRemoveMember } from "./api";
import { can } from "./can";

/**
 * /c/:slug/members (E07-S04): who is in the club, owners first, searchable. Holders of MEMBER_REMOVE remove members,
 * never owners (BR-05). The club comes from the outlet context (see JoinRequestsPage).
 */
export function MembersPage() {
  const club = useOutletContext<{ id?: string; name?: string }>();
  const clubId = club.id ?? "";
  const [text, setText] = useState("");
  const query = useDebounce(text, 300);
  const { data: membership } = useMyMembership(clubId);
  const { data, isPending, error, hasNextPage, fetchNextPage, isFetchingNextPage } = useMembers(clubId, query);
  const [removing, setRemoving] = useState<Member | null>(null);
  const canRemove = can(membership, "MEMBER_REMOVE");
  const members = data?.pages.flatMap((page) => page.items ?? []) ?? [];
  const total = data?.pages[0]?.totalElements ?? 0;

  if (error instanceof ApiError && error.status === 403) {
    return <EmptyState icon={LockIcon} title="Members only" description="Join the club to see who is in it." />;
  }
  return (
    <>
      <PageHeader title="Members" description={data ? `${total} ${total === 1 ? "member" : "members"}${query.trim() ? " found" : ""}` : undefined} />
      <Input type="search" aria-label="Search members" placeholder="Name or login…" className="mb-4 max-w-sm"
        value={text} onChange={(event) => setText(event.target.value)} />
      {isPending ? (
        <Skeleton className="h-40" aria-hidden="true" />
      ) : members.length === 0 ? (
        <EmptyState icon={UsersIcon} title="Nobody found" description="Try another name or login." />
      ) : (
        <ul aria-label="Members" className="divide-y rounded-xl border">
          {members.map((member) => (
            <li key={member.userId} className="flex flex-wrap items-center gap-3 p-3">
              <ClubAvatar name={member.displayName ?? ""} url={member.avatarUrl} size="sm" />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm">
                  <span className="font-medium">{member.displayName}</span> <span className="text-muted-foreground">@{member.login}</span>
                </p>
                {member.joinedAt && <p className="text-xs text-muted-foreground">Joined {formatDate(member.joinedAt)}</p>}
              </div>
              <div className="flex flex-wrap gap-1">
                {member.roles?.filter((role) => role.name !== "Member").map((role) => (
                  <Badge key={role.id} variant={role.system ? "default" : "secondary"}>{role.name}</Badge>
                ))}
              </div>
              {canRemove && !member.owner && (
                <Button variant="ghost" size="sm" aria-label={`Remove ${member.displayName}`} onClick={() => setRemoving(member)}>
                  Remove
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      {hasNextPage && (
        <div className="mt-4 flex justify-center">
          <Button variant="outline" disabled={isFetchingNextPage} onClick={() => void fetchNextPage()}>
            {isFetchingNextPage ? "Loading…" : "More members"}
          </Button>
        </div>
      )}
      <RemoveMemberDialog clubId={clubId} clubName={club.name} member={removing} onClose={() => setRemoving(null)} />
    </>
  );
}

function RemoveMemberDialog({ clubId, clubName, member, onClose }: {
  clubId: string;
  clubName?: string;
  member: Member | null;
  onClose: () => void;
}) {
  const remove = useRemoveMember(clubId);
  // The dialog fades out after member becomes null: keep showing the last one meanwhile.
  const [shown, setShown] = useState(member);
  if (member && member !== shown) setShown(member);

  function onConfirm() {
    remove.mutate(member!.userId!, {
      onSuccess: () => {
        toast.success(`${member!.displayName} was removed.`);
        onClose();
      },
      onError: (error) => toast.error(error.message),
    });
  }

  return (
    <Dialog open={member !== null} onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Remove {shown?.displayName} from {clubName ?? "the club"}?</DialogTitle>
          <DialogDescription>
            What they posted stays. They can join again later, unless the club approves who joins.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button variant="destructive" disabled={remove.isPending} onClick={onConfirm}>Remove</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
