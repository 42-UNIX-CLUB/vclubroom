import { InboxIcon } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router";
import { toast } from "sonner";
import { formatDate } from "@/shared/lib/dates";
import { Button } from "@/shared/ui/button";
import { Card, CardContent } from "@/shared/ui/card";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/shared/ui/dialog";
import { EmptyState } from "@/shared/ui/EmptyState";
import { Field, FieldLabel } from "@/shared/ui/field";
import { PageHeader } from "@/shared/ui/PageHeader";
import { Skeleton } from "@/shared/ui/skeleton";
import { StatusBadge } from "@/shared/ui/StatusBadge";
import { Tabs, TabsList, TabsTrigger } from "@/shared/ui/tabs";
import { Textarea } from "@/shared/ui/textarea";
import { type AdminClub, type ClubStatusFilter, useAdminClubs, useArchiveClub } from "./api";

const STATUSES: { value: ClubStatusFilter; label: string }[] = [
  { value: "ACTIVE", label: "Active" },
  { value: "PENDING_APPROVAL", label: "Pending" },
  { value: "ARCHIVED", label: "Archived" },
  { value: "REJECTED", label: "Rejected" },
];

/** /admin/clubs (E15-S01): every club by status; active clubs can be archived (E14-S01, D-13). */
export function AdminClubsPage() {
  const [status, setStatus] = useState<ClubStatusFilter>("ACTIVE");
  const { data, isPending } = useAdminClubs(status);
  const [archiving, setArchiving] = useState<AdminClub | null>(null);

  return (
    <>
      <PageHeader title="All clubs" />
      <Tabs value={status} onValueChange={(value) => setStatus(value as ClubStatusFilter)} className="mb-4">
        <TabsList>
          {STATUSES.map(({ value, label }) => <TabsTrigger key={value} value={value}>{label}</TabsTrigger>)}
        </TabsList>
      </Tabs>
      {isPending && <Skeleton className="h-24" />}
      {data?.items?.length === 0 && <EmptyState icon={InboxIcon} title="No clubs with this status" />}
      <div className="space-y-2">
        {data?.items?.map((club) => (
          <Card key={club.id}>
            <CardContent className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <Link to={`/c/${club.slug}`} className="font-medium hover:underline">{club.name}</Link>
                {club.createdAt && <p className="text-xs text-muted-foreground">Created {formatDate(club.createdAt)}</p>}
              </div>
              <div className="flex items-center gap-2">
                <StatusBadge status={club.status as ClubStatusFilter} />
                {club.status === "ACTIVE" && (
                  <Button variant="outline" size="sm" onClick={() => setArchiving(club)}>Archive</Button>
                )}
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
      <ArchiveDialog club={archiving} onClose={() => setArchiving(null)} />
    </>
  );
}

function ArchiveDialog({ club, onClose }: { club: AdminClub | null; onClose: () => void }) {
  const archive = useArchiveClub();
  const [reason, setReason] = useState("");

  function close() {
    setReason("");
    onClose();
  }

  function onConfirm() {
    archive.mutate({ clubId: club!.id!, reason: reason.trim() }, {
      onSuccess: () => {
        toast.success(`${club!.name} was archived.`);
        close();
      },
      onError: (error) => toast.error(error.message),
    });
  }

  return (
    <Dialog open={club !== null} onOpenChange={(open) => { if (!open) close(); }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Archive {club?.name}?</DialogTitle>
          <DialogDescription>
            Its current era ends. Everything stays readable, but nothing new can be posted, and nobody can join. Pending
            join requests and ownership invitations are closed. The reason is shown on the club page.
          </DialogDescription>
        </DialogHeader>
        <Field>
          <FieldLabel htmlFor="archive-reason">Reason</FieldLabel>
          <Textarea id="archive-reason" rows={3} value={reason} onChange={(event) => setReason(event.target.value)} />
        </Field>
        <DialogFooter>
          <Button variant="ghost" onClick={close}>Cancel</Button>
          <Button variant="destructive" disabled={!reason.trim() || archive.isPending} onClick={onConfirm}>
            Archive club
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
