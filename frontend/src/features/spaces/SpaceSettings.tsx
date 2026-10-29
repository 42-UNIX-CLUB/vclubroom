import { ArrowDownIcon, ArrowUpIcon, LockIcon, PlusIcon } from "lucide-react";
import { useState } from "react";
import { useOutletContext } from "react-router";
import { toast } from "sonner";
import { useGroupNames } from "@/features/groups";
import { can, useMyMembership } from "@/features/membership";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/ui/card";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/shared/ui/dialog";
import { EmptyState } from "@/shared/ui/EmptyState";
import { Field, FieldLabel } from "@/shared/ui/field";
import { FullPageSpinner } from "@/shared/ui/FullPageSpinner";
import { Input } from "@/shared/ui/input";
import { AccessRuleEditor, audienceSentence } from "./AccessRuleEditor";
import {
  type Channel, type Rule, type Space, useAddChannel, useArchiveChannel, useArchiveSpace, useManagedSpaces, useSaveSpace,
  useUpdateChannel,
} from "./api";
import { ChannelIcon } from "./ChannelIcon";

type Confirm = { title: string; description: string; label: string; run: () => Promise<unknown> } | null;

/**
 * Club settings › Spaces (E05-S03, SPACE_MANAGE): the club's spaces in order, who can read and post each one, and
 * their channels. Nothing is deleted: archiving makes a space or channel read-only and hides it from the sidebar.
 */
export function SpaceSettings() {
  const club = useOutletContext<{ id?: string }>();
  const clubId = club.id ?? "";
  const { data: membership } = useMyMembership(clubId);
  const allowed = can(membership, "SPACE_MANAGE");
  const { data: spaces } = useManagedSpaces(allowed ? clubId : "");
  const names = useGroupNames(allowed ? clubId : "");
  const save = useSaveSpace(clubId);
  const [editing, setEditing] = useState<Space | "new" | null>(null);
  const [confirm, setConfirm] = useState<Confirm>(null);

  if (!membership) return <FullPageSpinner />;
  if (!allowed) {
    return <EmptyState icon={LockIcon} title="Only space managers see this" description="Ask an owner for a role with the Manage spaces permission." />;
  }
  if (!spaces) return <FullPageSpinner />;
  const active = spaces.filter((s) => !s.archived);

  function move(space: Space, by: number) {
    save.mutate({ spaceId: space.id, position: (space.position ?? 0) + by }, { onError: (error) => toast.error(error.message) });
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Button onClick={() => setEditing("new")}><PlusIcon aria-hidden="true" /> New space</Button>
      </div>
      <ul aria-label="Spaces" className="space-y-3">
        {spaces.map((space) => (
          <li key={space.id}>
            <SpaceCard clubId={clubId} space={space} sentence={audienceSentence(rule(space, "read"), rule(space, "write"), names)}
                       first={active[0]?.id === space.id} last={active.at(-1)?.id === space.id}
                       onMove={(by) => move(space, by)} onEdit={() => setEditing(space)} onConfirm={setConfirm} />
          </li>
        ))}
      </ul>
      {editing !== null && <SpaceDialog clubId={clubId} space={editing === "new" ? null : editing} onClose={() => setEditing(null)} />}
      <ConfirmDialog confirm={confirm} onClose={() => setConfirm(null)} />
    </div>
  );
}

function rule(space: Space, which: "read" | "write"): Rule {
  return which === "read"
    ? { level: space.readLevel ?? "CLUB_MEMBERS", groupIds: space.readGroupIds ?? [] }
    : { level: space.writeLevel ?? "CLUB_MEMBERS", groupIds: space.writeGroupIds ?? [] };
}

function SpaceCard({ clubId, space, sentence, first, last, onMove, onEdit, onConfirm }: {
  clubId: string;
  space: Space;
  sentence: string;
  first: boolean;
  last: boolean;
  onMove: (by: number) => void;
  onEdit: () => void;
  onConfirm: (confirm: Confirm) => void;
}) {
  const archive = useArchiveSpace(clubId);
  const archived = !!space.archived;
  return (
    <Card size="sm" className={archived ? "opacity-70" : undefined}>
      <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <CardTitle className="flex items-center gap-2">{space.name}{archived && <Badge variant="secondary">Archived</Badge>}</CardTitle>
          <CardDescription>{sentence}</CardDescription>
        </div>
        {!archived && (
          <div className="flex flex-wrap gap-1">
            <Button variant="ghost" size="icon-sm" aria-label={`Move ${space.name} up`} disabled={first} onClick={() => onMove(-1)}><ArrowUpIcon /></Button>
            <Button variant="ghost" size="icon-sm" aria-label={`Move ${space.name} down`} disabled={last} onClick={() => onMove(1)}><ArrowDownIcon /></Button>
            <Button variant="outline" size="sm" onClick={onEdit}>Edit</Button>
            <Button variant="ghost" size="sm" onClick={() => onConfirm({
              title: `Archive ${space.name}?`,
              description: "It becomes read-only and leaves the sidebar. What was posted stays readable for those who could read it.",
              label: "Archive space",
              run: () => archive.mutateAsync(space.id ?? ""),
            })}>Archive</Button>
          </div>
        )}
      </CardHeader>
      <CardContent className="space-y-2">
        <Channels clubId={clubId} space={space} onConfirm={onConfirm} />
      </CardContent>
    </Card>
  );
}

function Channels({ clubId, space, onConfirm }: { clubId: string; space: Space; onConfirm: (confirm: Confirm) => void }) {
  const update = useUpdateChannel(clubId);
  const archive = useArchiveChannel(clubId);
  const add = useAddChannel(clubId);
  const [renaming, setRenaming] = useState<Channel | null>(null);
  const [type, setType] = useState<"FORUM" | "CHAT">("FORUM");
  const [name, setName] = useState("");
  const channels = space.channels ?? [];
  const active = channels.filter((c) => !c.archived);
  const fail = (error: Error) => toast.error(error.message);

  function onAdd(event: React.FormEvent) {
    event.preventDefault();
    add.mutate({ spaceId: space.id ?? "", type, name: name.trim() }, { onSuccess: () => setName(""), onError: fail });
  }

  return (
    <>
      {channels.length > 0 && (
        <ul aria-label={`Channels of ${space.name}`} className="divide-y rounded-lg border">
          {channels.map((channel) => (
            <li key={channel.id} className="flex flex-wrap items-center gap-2 p-2 text-sm">
              <ChannelIcon type={channel.type} />
              <span className="min-w-0 flex-1 truncate">{channel.name}</span>
              {channel.archived ? <Badge variant="secondary">Archived</Badge> : !space.archived && (
                <span className="flex gap-1">
                  <Button variant="ghost" size="icon-sm" aria-label={`Move ${channel.name} up`} disabled={active[0]?.id === channel.id}
                          onClick={() => update.mutate({ channelId: channel.id ?? "", position: (channel.position ?? 0) - 1 }, { onError: fail })}><ArrowUpIcon /></Button>
                  <Button variant="ghost" size="icon-sm" aria-label={`Move ${channel.name} down`} disabled={active.at(-1)?.id === channel.id}
                          onClick={() => update.mutate({ channelId: channel.id ?? "", position: (channel.position ?? 0) + 1 }, { onError: fail })}><ArrowDownIcon /></Button>
                  <Button variant="ghost" size="sm" onClick={() => setRenaming(channel)}>Rename</Button>
                  <Button variant="ghost" size="sm" onClick={() => onConfirm({
                    title: `Archive #${channel.name}?`,
                    description: "It becomes read-only and leaves the sidebar.",
                    label: "Archive channel",
                    run: () => archive.mutateAsync(channel.id ?? ""),
                  })}>Archive</Button>
                </span>
              )}
            </li>
          ))}
        </ul>
      )}
      {!space.archived && (
        <form onSubmit={onAdd} className="flex flex-wrap gap-2">
          <select aria-label={`New channel type in ${space.name}`} value={type} onChange={(e) => setType(e.target.value as "FORUM" | "CHAT")}
                  className="h-8 rounded-lg border bg-background px-2 text-sm">
            <option value="FORUM">Forum</option>
            <option value="CHAT">Chat</option>
          </select>
          <Input aria-label={`New channel name in ${space.name}`} placeholder="Channel name" maxLength={60} className="h-8 max-w-48"
                 value={name} onChange={(e) => setName(e.target.value)} />
          <Button type="submit" size="sm" variant="outline" disabled={!name.trim() || add.isPending}>Add channel</Button>
        </form>
      )}
      {renaming && <RenameChannelDialog clubId={clubId} channel={renaming} onClose={() => setRenaming(null)} />}
    </>
  );
}

/** Create a space (no space) or edit its name, description and rules. */
function SpaceDialog({ clubId, space, onClose }: { clubId: string; space: Space | null; onClose: () => void }) {
  const save = useSaveSpace(clubId);
  const [name, setName] = useState(space?.name ?? "");
  const [description, setDescription] = useState(space?.description ?? "");
  const [read, setRead] = useState<Rule>(space ? rule(space, "read") : { level: "CLUB_MEMBERS", groupIds: [] });
  const [write, setWrite] = useState<Rule>(space ? rule(space, "write") : { level: "CLUB_MEMBERS", groupIds: [] });
  const emptyGroups = [read, write].some((r) => r.level === "GROUPS" && !(r.groupIds ?? []).length);

  function onSave() {
    save.mutate({ spaceId: space?.id, name: name.trim(), description: description.trim(), readRule: read, writeRule: write }, {
      onSuccess: () => {
        toast.success(space ? `${name.trim()} saved.` : `${name.trim()} created.`);
        onClose();
      },
      onError: (error) => toast.error(error.message),
    });
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{space ? `Edit ${space.name}` : "New space"}</DialogTitle>
          <DialogDescription>{space ? "Its address stays the same." : "Add channels to it once it is created."}</DialogDescription>
        </DialogHeader>
        <Field>
          <FieldLabel htmlFor="space-name">Name</FieldLabel>
          <Input id="space-name" maxLength={60} value={name} onChange={(e) => setName(e.target.value)} placeholder="Board room" />
        </Field>
        <Field>
          <FieldLabel htmlFor="space-description">Description (optional)</FieldLabel>
          <Input id="space-description" maxLength={500} value={description} onChange={(e) => setDescription(e.target.value)} />
        </Field>
        <AccessRuleEditor clubId={clubId} read={read} write={write} onChange={(r, w) => { setRead(r); setWrite(w); }} />
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button disabled={!name.trim() || emptyGroups || save.isPending} onClick={onSave}>{space ? "Save" : "Create space"}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function RenameChannelDialog({ clubId, channel, onClose }: { clubId: string; channel: Channel; onClose: () => void }) {
  const update = useUpdateChannel(clubId);
  const [name, setName] = useState(channel.name ?? "");
  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Rename #{channel.name}</DialogTitle>
          <DialogDescription>Its address stays the same.</DialogDescription>
        </DialogHeader>
        <Field>
          <FieldLabel htmlFor="channel-name">Name</FieldLabel>
          <Input id="channel-name" maxLength={60} value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button disabled={!name.trim() || update.isPending}
                  onClick={() => update.mutate({ channelId: channel.id ?? "", name: name.trim() }, { onSuccess: onClose, onError: (e) => toast.error(e.message) })}>
            Rename
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function ConfirmDialog({ confirm, onClose }: { confirm: Confirm; onClose: () => void }) {
  const [busy, setBusy] = useState(false);
  // The dialog fades out after confirm becomes null: keep showing the last one meanwhile.
  const [shown, setShown] = useState(confirm);
  if (confirm && confirm !== shown) setShown(confirm);

  async function onConfirm() {
    setBusy(true);
    try {
      await confirm!.run();
      onClose();
    } catch (error) {
      toast.error((error as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog open={confirm !== null} onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{shown?.title}</DialogTitle>
          <DialogDescription>{shown?.description}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button variant="destructive" disabled={busy} onClick={() => void onConfirm()}>{shown?.label}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
