import { HistoryIcon } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router";
import { toast } from "sonner";
import { formatRelative } from "@/shared/lib/dates";
import { Button } from "@/shared/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/ui/card";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/shared/ui/dialog";
import { EmptyState } from "@/shared/ui/EmptyState";
import { Field, FieldLabel } from "@/shared/ui/field";
import { PageHeader } from "@/shared/ui/PageHeader";
import { Skeleton } from "@/shared/ui/skeleton";
import { Textarea } from "@/shared/ui/textarea";
import { type RevivalRequest, useApproveRevival, useRejectRevival, useRevivalRequests } from "./api";

/**
 * /admin/revivals (E14-S02-T3): students asking to bring an archived club back, oldest first. Approving one starts
 * the club's next era with that student as owner, and turns the club's other requests down (BR-20).
 */
export function RevivalQueuePage() {
  const { data, isPending, isError } = useRevivalRequests();
  const [rejecting, setRejecting] = useState<RevivalRequest | null>(null);

  return (
    <>
      <PageHeader title="Revival requests" description="Requests are listed oldest first." />
      {isPending && <Skeleton className="h-32" />}
      {isError && <p className="text-sm text-destructive">Could not load the requests.</p>}
      {data?.items?.length === 0 && (
        <EmptyState icon={HistoryIcon} title="Nothing to review" description="Requests to revive archived clubs appear here." />
      )}
      <div className="space-y-4">
        {data?.items?.map((request) => <RevivalCard key={request.id} request={request} onReject={() => setRejecting(request)} />)}
      </div>
      <RejectRevivalDialog request={rejecting} onClose={() => setRejecting(null)} />
    </>
  );
}

function RevivalCard({ request, onReject }: { request: RevivalRequest; onReject: () => void }) {
  const approve = useApproveRevival();

  function onApprove() {
    approve.mutate(request.id!, {
      onSuccess: (club) => toast.success(`${club.name} is active again, in era ${club.currentEra}.`),
      onError: (error) => toast.error(error.message),
    });
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="space-y-1">
            <CardTitle><Link to={`/c/${request.clubSlug}`} className="hover:underline">{request.clubName}</Link></CardTitle>
            <CardDescription>
              {request.requesterName ?? "A student"} wants to revive it
              {request.createdAt && ` · ${formatRelative(request.createdAt)}`}
            </CardDescription>
          </div>
          <div className="flex gap-2">
            <Button variant="outline" onClick={onReject}>Reject</Button>
            <Button onClick={onApprove} disabled={approve.isPending}>Approve</Button>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <p className="text-sm whitespace-pre-wrap">{request.motivation}</p>
      </CardContent>
    </Card>
  );
}

function RejectRevivalDialog({ request, onClose }: { request: RevivalRequest | null; onClose: () => void }) {
  const reject = useRejectRevival();
  const [reason, setReason] = useState("");

  function close() {
    setReason("");
    onClose();
  }

  function onConfirm() {
    reject.mutate({ requestId: request!.id!, reason: reason.trim() }, {
      onSuccess: () => {
        toast.success("The request was turned down.");
        close();
      },
      onError: (error) => toast.error(error.message),
    });
  }

  return (
    <Dialog open={request !== null} onOpenChange={(open) => { if (!open) close(); }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Turn down the revival of {request?.clubName}?</DialogTitle>
          <DialogDescription>The reason stays with the request, and {request?.requesterName ?? "the student"} may ask again later.</DialogDescription>
        </DialogHeader>
        <Field>
          <FieldLabel htmlFor="revival-reject-reason">Reason</FieldLabel>
          <Textarea id="revival-reject-reason" rows={4} value={reason} onChange={(event) => setReason(event.target.value)} />
        </Field>
        <DialogFooter>
          <Button variant="ghost" onClick={close}>Cancel</Button>
          <Button variant="destructive" disabled={!reason.trim() || reject.isPending} onClick={onConfirm}>
            Turn down
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
