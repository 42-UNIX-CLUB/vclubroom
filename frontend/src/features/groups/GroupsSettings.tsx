import { LockIcon, UsersIcon } from "lucide-react";
import { useState } from "react";
import { useOutletContext } from "react-router";
import { toast } from "sonner";
import { can, useMyMembership, UserPicker } from "@/features/membership";
import { Button } from "@/shared/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/ui/card";
import { ClubAvatar } from "@/shared/ui/ClubAvatar";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/shared/ui/dialog";
import { EmptyState } from "@/shared/ui/EmptyState";
import { FullPageSpinner } from "@/shared/ui/FullPageSpinner";
import { Input } from "@/shared/ui/input";
import { type ClubGroup, useClubGroups, useDeleteGroup, useGroupMembers, useGroupMembership, useSaveGroup } from "./api";

/**
 * Club settings › Groups (E13-S03, GROUP_MANAGE): named sets of members, like "Board". Groups grant no permissions:
 * they only open spaces to some members (Settings › Spaces). The club comes from the outlet context.
 */
export function GroupsSettings() {
  const club = useOutletContext<{ id?: string }>();
  const clubId = club.id ?? "";
  const { data: membership } = useMyMembership(clubId);
  const allowed = can(membership, "GROUP_MANAGE");
  const { data: groups } = useClubGroups(allowed ? clubId : "");
  const save = useSaveGroup(clubId);
  const [name, setName] = useState("");
  const [deleting, setDeleting] = useState<ClubGroup | null>(null);

  if (!membership) return <FullPageSpinner />;
  if (!allowed) {
    return <EmptyState icon={LockIcon} title="Only group managers see this" description="Ask an owner for a role with the Manage groups permission." />;
  }
  if (!groups) return <FullPageSpinner />;

  function onCreate(event: React.FormEvent) {
    event.preventDefault();
    save.mutate({ name: name.trim() }, {
      onSuccess: (group) => {
        toast.success(`${group.name} created.`);
        setName("");
      },
      onError: (error) => toast.error(error.message),
    });
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        Groups open spaces to some members only (Settings › Spaces). They grant no permissions.
      </p>
      <form onSubmit={onCreate} className="flex flex-wrap gap-2">
        <Input aria-label="New group name" placeholder="Board, Robotics team…" maxLength={80} className="max-w-xs"
               value={name} onChange={(event) => setName(event.target.value)} />
        <Button type="submit" disabled={!name.trim() || save.isPending}>Create group</Button>
      </form>
      {groups.length === 0
        ? <EmptyState icon={UsersIcon} title="No groups yet" description="Create one, then add members to it." />
        : (
          <ul aria-label="Groups" className="space-y-3">
            {groups.map((group) => (
              <li key={group.id}><GroupCard clubId={clubId} group={group} onDelete={() => setDeleting(group)} /></li>
            ))}
          </ul>
        )}
      <DeleteGroupDialog clubId={clubId} group={deleting} onClose={() => setDeleting(null)} />
    </div>
  );
}

function GroupCard({ clubId, group, onDelete }: { clubId: string; group: ClubGroup; onDelete: () => void }) {
  const [open, setOpen] = useState(false);
  const count = group.memberCount ?? 0;
  return (
    <Card size="sm">
      <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-2">
        <div>
          <CardTitle>{group.name}</CardTitle>
          <CardDescription>{count} {count === 1 ? "member" : "members"}{group.description && ` · ${group.description}`}</CardDescription>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
            {open ? "Hide members" : "Members"}
          </Button>
          <Button variant="ghost" size="sm" onClick={onDelete}>Delete</Button>
        </div>
      </CardHeader>
      {open && <CardContent><GroupMembers clubId={clubId} group={group} /></CardContent>}
    </Card>
  );
}

/** The group's members, each removable, and a member search to add more (owners included). */
function GroupMembers({ clubId, group }: { clubId: string; group: ClubGroup }) {
  const groupId = group.id ?? "";
  const { data: members } = useGroupMembers(clubId, groupId);
  const membership = useGroupMembership(clubId, groupId);

  function change(userId: string, add: boolean, name?: string) {
    membership.mutate({ userId, add }, {
      onSuccess: () => toast.success(add ? `${name} added to ${group.name}.` : `${name} removed from ${group.name}.`),
      onError: (error) => toast.error(error.message),
    });
  }

  return (
    <div className="space-y-3">
      {members && members.length > 0 && (
        <ul aria-label={`Members of ${group.name}`} className="divide-y rounded-lg border">
          {members.map(({ user }) => (
            <li key={user?.id} className="flex items-center gap-3 p-2 text-sm">
              <ClubAvatar name={user?.displayName ?? ""} url={user?.avatarUrl} size="sm" />
              <span className="min-w-0 flex-1 truncate">{user?.displayName} <span className="text-muted-foreground">@{user?.login}</span></span>
              <Button variant="ghost" size="sm" aria-label={`Remove ${user?.displayName}`} disabled={membership.isPending}
                      onClick={() => change(user?.id ?? "", false, user?.displayName)}>Remove</Button>
            </li>
          ))}
        </ul>
      )}
      <UserPicker clubId={clubId} includeOwners label={`Add a member to ${group.name}`} value={null}
                  onChange={(picked) => { if (picked?.id) change(picked.id, true, picked.displayName); }} />
    </div>
  );
}

function DeleteGroupDialog({ clubId, group, onClose }: { clubId: string; group: ClubGroup | null; onClose: () => void }) {
  const remove = useDeleteGroup(clubId);
  // The dialog fades out after group becomes null: keep showing the last one meanwhile.
  const [shown, setShown] = useState(group);
  if (group && group !== shown) setShown(group);

  function onConfirm() {
    remove.mutate(group!.id!, {
      onSuccess: () => {
        toast.success(`${group!.name} deleted.`);
        onClose();
      },
      onError: (error) => toast.error(error.message),
    });
  }

  return (
    <Dialog open={group !== null} onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Delete {shown?.name}?</DialogTitle>
          <DialogDescription>
            Spaces open only to this group close for its members, until you choose another audience in Settings › Spaces.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button variant="destructive" disabled={remove.isPending} onClick={onConfirm}>Delete group</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
