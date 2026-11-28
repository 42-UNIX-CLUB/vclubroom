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
import { type ClubPermission, type ClubRole, type Member, useAssignRoles, useMembers, useMyMembership, useRemoveMember, useRoles } from "./api";
import { can } from "./can";

/**
 * /c/:slug/members (E07-S04): who is in the club, owners first, searchable. Holders of MEMBER_REMOVE remove members,
 * never owners (BR-05); holders of ROLE_MANAGE give out the club's custom roles (E13-S02). The club comes from the
 * outlet context (see JoinRequestsPage).
 */
export function MembersPage() {
  const club = useOutletContext<{ id?: string; name?: string }>();
  const clubId = club.id ?? "";
  const [text, setText] = useState("");
  const query = useDebounce(text, 300);
  const { data: membership } = useMyMembership(clubId);
  const { data, isPending, error, hasNextPage, fetchNextPage, isFetchingNextPage } = useMembers(clubId, query);
  const [removing, setRemoving] = useState<Member | null>(null);
  const [assigning, setAssigning] = useState<Member | null>(null);
  const canRemove = can(membership, "MEMBER_REMOVE");
  const canAssign = can(membership, "ROLE_MANAGE");
  const members = data?.pages.flatMap((page) => page.items ?? []) ?? [];
  const total = data?.pages[0]?.totalElements ?? 0;

  if (error instanceof ApiError && error.status === 403) {
    return <EmptyState icon={LockIcon} title="Members only" description="Join the club to see who is in it." />;
  }
  return (
    <>
      <PageHeader level={2} title="Members" description={data ? `${total} ${total === 1 ? "member" : "members"}${query.trim() ? " found" : ""}` : undefined} />
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
              {canAssign && !member.owner && (
                <Button variant="ghost" size="sm" aria-label={`Roles of ${member.displayName}`} onClick={() => setAssigning(member)}>
                  Roles
                </Button>
              )}
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
      {assigning && (
        <AssignRolesDialog clubId={clubId} member={assigning} held={new Set(membership?.permissions ?? [])}
                           onClose={() => setAssigning(null)} />
      )}
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

/**
 * Which custom roles a member holds. A role that can do more than you can is greyed out, unless they already hold it:
 * taking a role away is always allowed (BR-14).
 */
function AssignRolesDialog({ clubId, member, held, onClose }: {
  clubId: string;
  member: Member;
  held: Set<ClubPermission>;
  onClose: () => void;
}) {
  const { data: roles = [] } = useRoles(clubId);
  const assign = useAssignRoles(clubId);
  const holds = new Set((member.roles ?? []).filter((role) => !role.system).map((role) => role.id));
  const [chosen, setChosen] = useState<Set<string | undefined>>(holds);
  const custom = roles.filter((role) => !role.system);
  const within = (role: ClubRole) => (role.permissions ?? []).every((p) => held.has(p));

  function toggle(roleId: string | undefined, on: boolean) {
    const next = new Set(chosen);
    if (on) next.add(roleId);
    else next.delete(roleId);
    setChosen(next);
  }

  function onSave() {
    assign.mutate({ userId: member.userId!, roleIds: [...chosen].filter((id): id is string => !!id) }, {
      onSuccess: () => {
        toast.success(`Roles of ${member.displayName} saved.`);
        onClose();
      },
      onError: (error) => toast.error(error.message),
    });
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Roles of {member.displayName}</DialogTitle>
          <DialogDescription>Everyone is a Member; owners are made in Settings › Ownership.</DialogDescription>
        </DialogHeader>
        {custom.length === 0
          ? <p className="text-sm text-muted-foreground">The club has no custom roles yet: create them in Settings › Roles.</p>
          : (
            <fieldset className="space-y-2">
              <legend className="sr-only">Custom roles</legend>
              {custom.map((role) => {
                const allowed = within(role) || holds.has(role.id);
                return (
                  <label key={role.id} className={`flex items-center gap-3 rounded-lg border p-2 text-sm ${allowed ? "" : "opacity-60"}`}>
                    <input type="checkbox" className="accent-primary" checked={chosen.has(role.id)} disabled={!allowed}
                           onChange={(event) => toggle(role.id, event.target.checked)} />
                    <span aria-hidden="true" className="size-3 rounded-full" style={{ backgroundColor: role.color ?? "#64748b" }} />
                    {role.name}
                    {!allowed && <span className="text-muted-foreground">(can do more than you)</span>}
                  </label>
                );
              })}
            </fieldset>
          )}
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button disabled={assign.isPending || custom.length === 0} onClick={onSave}>Save roles</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
