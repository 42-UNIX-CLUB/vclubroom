import { InboxIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { formatRelative } from "@/shared/lib/dates";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/ui/card";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/shared/ui/dialog";
import { EmptyState } from "@/shared/ui/EmptyState";
import { Field, FieldLabel } from "@/shared/ui/field";
import { Markdown } from "@/shared/ui/Markdown";
import { PageHeader } from "@/shared/ui/PageHeader";
import { Skeleton } from "@/shared/ui/skeleton";
import { Textarea } from "@/shared/ui/textarea";
import { type AdminClub, useAdminClubs, useApproveClub, useRejectClub } from "./api";

/** /admin/approvals (E04-S02-T4): pending club requests, oldest first, approve or reject with a reason. */
export function ApprovalQueuePage() {
  const { data, isPending, isError } = useAdminClubs("PENDING_APPROVAL");
  const [rejecting, setRejecting] = useState<AdminClub | null>(null);

  return (
    <>
      <PageHeader title="Club approvals" description="Requests are listed oldest first." />
      {isPending && <Skeleton className="h-32" />}
      {isError && <p className="text-sm text-destructive">Could not load the requests.</p>}
      {data?.items?.length === 0 && (
        <EmptyState icon={InboxIcon} title="Nothing to review" description="New club requests appear here." />
      )}
      <div className="space-y-4">
        {data?.items?.map((club) => <PendingClubCard key={club.id} club={club} onReject={() => setRejecting(club)} />)}
      </div>
      <RejectDialog club={rejecting} onClose={() => setRejecting(null)} />
    </>
  );
}

function PendingClubCard({ club, onReject }: { club: AdminClub; onReject: () => void }) {
  const approve = useApproveClub();
  const [showDescription, setShowDescription] = useState(false);

  function onApprove() {
    approve.mutate(club.id!, {
      onSuccess: () => toast.success(`${club.name} is now active.`),
      onError: (error) => toast.error(error.message),
    });
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="space-y-1">
            <CardTitle>{club.name}</CardTitle>
            {club.tagline && <CardDescription>{club.tagline}</CardDescription>}
          </div>
          <div className="flex gap-2">
            <Button variant="outline" onClick={onReject}>Reject</Button>
            <Button onClick={onApprove} disabled={approve.isPending}>Approve</Button>
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="flex flex-wrap gap-1">
          <Badge>{club.category}</Badge>
          {club.tags?.map((tag) => <Badge key={tag} variant="secondary">#{tag}</Badge>)}
        </div>
        {club.createdAt && <p className="text-xs text-muted-foreground">Requested {formatRelative(club.createdAt)}</p>}
        {club.description && (showDescription
          ? <Markdown>{club.description}</Markdown>
          : <Button variant="link" className="h-auto px-0" onClick={() => setShowDescription(true)}>Show description</Button>)}
      </CardContent>
    </Card>
  );
}

function RejectDialog({ club, onClose }: { club: AdminClub | null; onClose: () => void }) {
  const reject = useRejectClub();
  const [reason, setReason] = useState("");

  function close() {
    setReason("");
    onClose();
  }

  function onConfirm() {
    reject.mutate({ clubId: club!.id!, reason: reason.trim() }, {
      onSuccess: () => {
        toast.success(`${club!.name} was rejected.`);
        close();
      },
      onError: (error) => toast.error(error.message),
    });
  }

  return (
    <Dialog open={club !== null} onOpenChange={(open) => { if (!open) close(); }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Reject {club?.name}?</DialogTitle>
          <DialogDescription>The founder sees this reason in "My requests". A rejection is final.</DialogDescription>
        </DialogHeader>
        <Field>
          <FieldLabel htmlFor="reject-reason">Reason</FieldLabel>
          <Textarea id="reject-reason" rows={4} value={reason} onChange={(event) => setReason(event.target.value)} />
        </Field>
        <DialogFooter>
          <Button variant="ghost" onClick={close}>Cancel</Button>
          <Button variant="destructive" disabled={!reason.trim() || reject.isPending} onClick={onConfirm}>
            Reject club
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
