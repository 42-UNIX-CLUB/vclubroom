import { PlusIcon, RefreshCwIcon, UsersIcon, XIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { UserPicker } from "@/features/membership";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/ui/card";
import { ClubAvatar } from "@/shared/ui/ClubAvatar";
import { ConfirmDialog } from "@/shared/ui/ConfirmDialog";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/shared/ui/dialog";
import { EmptyState } from "@/shared/ui/EmptyState";
import { Field, FieldLabel } from "@/shared/ui/field";
import { Input } from "@/shared/ui/input";
import { PageHeader } from "@/shared/ui/PageHeader";
import { Skeleton } from "@/shared/ui/skeleton";
import {
  type CampusGroup, useAdminCampusGroups, useCampusGroupMembers, useCampusGroupMembership, useDeleteCampusGroup, useSaveCampusGroup,
} from "./api";

const memberCount = (n = 0) => `${n} ${n === 1 ? "member" : "members"}`;

/**
 * /admin/campus-groups (E15-S03): campus-wide groups that clubs can open spaces to. Manual ones ("Tutors", "BDE") are
 * created, renamed and filled here; synced ones follow 42 profiles at each login (E13-S04) and are read-only.
 */
export function CampusGroupsPage() {
  const { data: groups, isPending } = useAdminCampusGroups();
  const [editing, setEditing] = useState<CampusGroup | "new" | null>(null);
  const manual = groups?.filter((g) => !g.synced) ?? [];
  const synced = groups?.filter((g) => g.synced) ?? [];

  return (
    <>
      <PageHeader title="Campus groups" actions={<Button onClick={() => setEditing("new")}><PlusIcon aria-hidden="true" /> New group</Button>} />
  {isPending && <Skeleton className="h-24" />}
  <section aria-labelledby="manual-heading" className="mb-8 space-y-3">
  <h2 id="manual-heading" className="text-lg font-semibold">Manual groups</h2>
  {groups && manual.length === 0 && <EmptyState icon={UsersIcon} title="No manual groups yet" description="Create one for staff, tutors or a student association." />}
  <ul className="space-y-3">{manual.map((group) => <li key={group.id}><ManualGroupCard group={group} onEdit={() => setEditing(group)} /></li>)}</ul>
  </section>
  <section aria-labelledby="synced-heading" className="space-y-3">
  <h2 id="synced-heading" className="text-lg font-semibold">Synced from 42</h2>
  <p className="text-sm text-muted-foreground">Campus, pool, cursus and alumni groups, kept up to date at each 42 login.</p>
  {groups && synced.length === 0 && <p className="text-sm text-muted-foreground">None yet: they appear as students log in with 42.</p>}
  {synced.length > 0 && <ul className="divide-y rounded-xl border">
    {synced.map((group) => (
        <li key={group.id} className="flex items-center justify-between gap-2 px-3 py-2 text-sm">
      <span className="flex items-center gap-2"><RefreshCwIcon className="size-3.5 text-muted-foreground" aria-hidden="true" /> {group.name}</span>
        <span className="text-muted-foreground">{memberCount(group.memberCount)}</span>
  </li>
  ))}
    </ul>}
    </section>
    {editing !== null && <GroupDialog group={editing === "new" ? null : editing} onClose={() => setEditing(null)} />}
    </>
    );
    }

    function ManualGroupCard({ group, onEdit }: { group: CampusGroup; onEdit: () => void }) {
      const [open, setOpen] = useState(false);
      const [deleting, setDeleting] = useState(false);
      const remove = useDeleteCampusGroup();
      return (
        <Card size="sm">
        <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
        <CardTitle className="flex items-center gap-2">{group.name}<Badge variant="secondary">{memberCount(group.memberCount)}</Badge></CardTitle>
      {group.description && <CardDescription>{group.description}</CardDescription>}
          </div>
          <div className="flex flex-wrap gap-1">
        <Button variant="outline" size="sm" aria-expanded={open} onClick={() => setOpen((o) => !o)}>{open ? "Hide members" : "Members"}</Button>
      <Button variant="ghost" size="sm" onClick={onEdit}>Rename</Button>
        <Button variant="ghost" size="sm" onClick={() => setDeleting(true)}>Delete</Button>
      </div>
      </CardHeader>
      {open && <CardContent><Members group={group} /></CardContent>}
      <ConfirmDialog open={deleting} onClose={() => setDeleting(false)} title={`Delete ${group.name}?`} confirmLabel="Delete group"
      description="Spaces that let this group in stop doing so; if it was a rule's only group, nobody passes that rule until it is changed."
      onConfirm={() => remove.mutateAsync(group.id ?? "")} />
      </Card>
    );
    }

      function Members({ group }: { group: CampusGroup }) {
        const groupId = group.id ?? "";
        const { data: members = [] } = useCampusGroupMembers(groupId, true);
        const membership = useCampusGroupMembership(groupId);

        function change(userId: string, member: boolean, name?: string) {
          membership.mutate({ userId, member }, {
            onSuccess: () => { if (member) toast.success(`${name ?? "They"} joined ${group.name}.`); },
            onError: (error) => toast.error(error.message),
          });
        }

        return (
          <div className="space-y-3">
            {members.length === 0
                ? <p className="text-sm text-muted-foreground">Nobody yet.</p>
      : (
          <ul aria-label={`Members of ${group.name}`} className="flex flex-wrap gap-2">
          {members.map(({ user }) => (
              <li key={user?.id} className="flex items-center gap-1.5 rounded-full border py-0.5 pr-1 pl-0.5 text-sm">
        <ClubAvatar name={user?.displayName ?? ""} url={user?.avatarUrl} size="sm" />
          {user?.displayName}
        <Button variant="ghost" size="icon-xs" aria-label={`Remove ${user?.displayName}`} onClick={() => change(user?.id ?? "", false)}><XIcon /></Button>
        </li>
      ))}
        </ul>
      )}
        <UserPicker label={`Add someone to ${group.name}`} value={null}
        onChange={(picked) => { if (picked?.id) change(picked.id, true, picked.displayName); }} />
        </div>
      );
      }

      function GroupDialog({ group, onClose }: { group: CampusGroup | null; onClose: () => void }) {
        const save = useSaveCampusGroup();
        const [name, setName] = useState(group?.name ?? "");
        const [description, setDescription] = useState(group?.description ?? "");

        function onSave() {
          save.mutate({ groupId: group?.id, name: name.trim(), description: description.trim() }, {
            onSuccess: () => onClose(),
            onError: (error) => toast.error(error.message),
          });
        }

        return (
          <Dialog open onOpenChange={(open) => { if (!open) onClose(); }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{group ? `Rename ${group.name}` : "New campus group"}</DialogTitle>
        <DialogDescription>Clubs can open their spaces to it in Settings › Spaces.</DialogDescription>
        </DialogHeader>
        <Field>
        <FieldLabel htmlFor="campus-group-name">Name</FieldLabel>
          <Input id="campus-group-name" maxLength={80} value={name} onChange={(e) => setName(e.target.value)} placeholder="Tutors" />
        </Field>
        <Field>
        <FieldLabel htmlFor="campus-group-description">Description (optional)</FieldLabel>
          <Input id="campus-group-description" maxLength={500} value={description} onChange={(e) => setDescription(e.target.value)} />
        </Field>
        <DialogFooter>
        <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button disabled={!name.trim() || save.isPending} onClick={onSave}>{group ? "Save" : "Create group"}</Button>
          </DialogFooter>
          </DialogContent>
          </Dialog>
      );
      }
