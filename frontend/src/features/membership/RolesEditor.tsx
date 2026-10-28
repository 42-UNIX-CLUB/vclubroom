import { CrownIcon, LockIcon, PlusIcon } from "lucide-react";
import { useState } from "react";
import { useOutletContext } from "react-router";
import { toast } from "sonner";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/shared/ui/dialog";
import { EmptyState } from "@/shared/ui/EmptyState";
import { Field, FieldLabel } from "@/shared/ui/field";
import { FullPageSpinner } from "@/shared/ui/FullPageSpinner";
import { Input } from "@/shared/ui/input";
import {
  type ClubPermission, type ClubRole, type PermissionInfo, useDeleteRole, useMyMembership, usePermissions, useRoles, useSaveRole,
} from "./api";
import { can } from "./can";

const COLORS = ["#10b981", "#3b82f6", "#8b5cf6", "#ec4899", "#f97316", "#64748b"];

/**
 * Club settings › Roles (E13-S02, ROLE_MANAGE): the club's roles and what each may do. Owner can do everything and
 * never changes; Member keeps its name, but its permissions are every member's; custom roles are yours to shape.
 * Permissions you do not hold are greyed out: nobody hands out more than they have (BR-14).
 */
export function RolesEditor() {
  const club = useOutletContext<{ id?: string }>();
  const clubId = club.id ?? "";
  const { data: membership } = useMyMembership(clubId);
  const allowed = can(membership, "ROLE_MANAGE");
  const { data: roles } = useRoles(clubId, allowed);
  const { data: catalogue = [] } = usePermissions();
  const [editing, setEditing] = useState<ClubRole | "new" | null>(null);
  const [deleting, setDeleting] = useState<ClubRole | null>(null);

  if (!membership) return <FullPageSpinner />;
  if (!allowed) {
    return <EmptyState icon={LockIcon} title="Only role managers see this" description="Ask an owner for a role with the Manage roles permission." />;
  }
  if (!roles) return <FullPageSpinner />;
  const labels = new Map(catalogue.map((p) => [p.code, p.label]));
  const held = new Set(membership.permissions ?? []);

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Button onClick={() => setEditing("new")}><PlusIcon aria-hidden="true" /> New role</Button>
      </div>
      <ul aria-label="Roles" className="space-y-3">
        {roles.map((role) => (
          <li key={role.id}>
            <Card size="sm">
              <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-2">
                <CardTitle className="flex items-center gap-2">
                  <span aria-hidden="true" className="size-3 rounded-full" style={{ backgroundColor: role.color ?? "#64748b" }} />
                  {role.owner && <CrownIcon className="size-4 text-amber-500" aria-hidden="true" />}
                  {role.name}
                  <span className="text-sm font-normal text-muted-foreground">
                    {role.memberCount} {role.memberCount === 1 ? "member" : "members"}
                  </span>
                </CardTitle>
                {!role.owner && (
                  <div className="flex gap-2">
                    <Button variant="outline" size="sm" onClick={() => setEditing(role)}>
                      {role.system ? "Edit permissions" : "Edit"}
                    </Button>
                    {!role.system && <Button variant="ghost" size="sm" onClick={() => setDeleting(role)}>Delete</Button>}
                  </div>
                )}
              </CardHeader>
              <CardContent className="flex flex-wrap gap-1">
                {role.owner
                  ? <p className="text-sm text-muted-foreground">Every permission, always.</p>
                  : role.permissions?.length
                    ? role.permissions.map((p) => <Badge key={p} variant="secondary">{labels.get(p) ?? p}</Badge>)
                    : <p className="text-sm text-muted-foreground">No extra permissions: reading and writing where the spaces allow.</p>}
              </CardContent>
            </Card>
          </li>
        ))}
      </ul>
      {editing !== null && (
        <RoleDialog clubId={clubId} role={editing === "new" ? null : editing} catalogue={catalogue} held={held}
                    onClose={() => setEditing(null)} />
      )}
      <DeleteRoleDialog clubId={clubId} role={deleting} onClose={() => setDeleting(null)} />
    </div>
  );
}

function RoleDialog({ clubId, role, catalogue, held, onClose }: {
  clubId: string;
  role: ClubRole | null;
  catalogue: PermissionInfo[];
  held: Set<ClubPermission>;
  onClose: () => void;
}) {
  const save = useSaveRole(clubId);
  const [name, setName] = useState(role?.name ?? "");
  const [color, setColor] = useState(role?.color ?? COLORS[0]!);
  const [permissions, setPermissions] = useState<Set<ClubPermission>>(new Set(role?.permissions ?? []));
  const system = role?.system ?? false;

  function toggle(code: ClubPermission, on: boolean) {
    const next = new Set(permissions);
    if (on) next.add(code);
    else next.delete(code);
    setPermissions(next);
  }

  function onSave() {
    const body = system ? { permissions: [...permissions] } : { name: name.trim(), color, permissions: [...permissions] };
    save.mutate({ roleId: role?.id, body }, {
      onSuccess: (saved) => {
        toast.success(role ? `${saved.name} saved.` : `${saved.name} created.`);
        onClose();
      },
      onError: (error) => toast.error(error.message),
    });
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{role ? `Edit ${role.name}` : "New role"}</DialogTitle>
          <DialogDescription>
            {system ? "Every member holds this role: what you allow here, every member may do." : "Give it a name, a colour and what it may do."}
          </DialogDescription>
        </DialogHeader>
        {!system && (
          <>
            <Field>
              <FieldLabel htmlFor="role-name">Name</FieldLabel>
              <Input id="role-name" maxLength={40} value={name} onChange={(event) => setName(event.target.value)} placeholder="Event lead" />
            </Field>
            <div role="radiogroup" aria-label="Colour" className="flex gap-2">
              {COLORS.map((c) => (
                <button key={c} type="button" role="radio" aria-checked={color === c} aria-label={c} onClick={() => setColor(c)}
                        className="size-7 rounded-full outline-none ring-offset-2 focus-visible:ring-3 focus-visible:ring-ring/50 aria-checked:ring-2 aria-checked:ring-foreground"
                        style={{ backgroundColor: c }} />
              ))}
            </div>
          </>
        )}
        <fieldset className="space-y-2">
          <legend className="mb-2 text-sm font-medium">Permissions</legend>
          {catalogue.map((p) => {
            const mine = held.has(p.code!);
            return (
              <label key={p.code} className={`flex items-start gap-3 rounded-lg border p-2 text-sm ${mine ? "" : "opacity-60"}`}>
                <input type="checkbox" className="mt-1 accent-primary" checked={permissions.has(p.code!)} disabled={!mine}
                       onChange={(event) => toggle(p.code!, event.target.checked)} />
                <span>
                  <span className="block font-medium">{p.label}</span>
                  <span className="block text-muted-foreground">{p.description}{!mine && " You don't have this permission yourself."}</span>
                </span>
              </label>
            );
          })}
        </fieldset>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button disabled={(!system && !name.trim()) || save.isPending} onClick={onSave}>{role ? "Save" : "Create role"}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function DeleteRoleDialog({ clubId, role, onClose }: { clubId: string; role: ClubRole | null; onClose: () => void }) {
  const remove = useDeleteRole(clubId);
  // The dialog fades out after role becomes null: keep showing the last one meanwhile.
  const [shown, setShown] = useState(role);
  if (role && role !== shown) setShown(role);

  function onConfirm() {
    remove.mutate(role!.id!, {
      onSuccess: () => {
        toast.success(`${role!.name} deleted.`);
        onClose();
      },
      onError: (error) => toast.error(error.message),
    });
  }

  return (
    <Dialog open={role !== null} onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Delete {shown?.name}?</DialogTitle>
          <DialogDescription>
            {shown?.memberCount ? `${shown.memberCount} ${shown.memberCount === 1 ? "member loses" : "members lose"} it, and what it allowed.` : "Nobody holds it."}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button variant="destructive" disabled={remove.isPending} onClick={onConfirm}>Delete role</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
