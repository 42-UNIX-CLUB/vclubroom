import { CalendarClockIcon, LinkIcon, LockIcon, MapPinIcon, SearchXIcon } from "lucide-react";
import { useState } from "react";
import { Link, useParams } from "react-router";
import { toast } from "sonner";
import { MediaView } from "@/features/media";
import { ApiError } from "@/shared/api/errors";
import { formatDate, formatDateTime, formatTime } from "@/shared/lib/dates";
import { cn } from "@/shared/lib/utils";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/shared/ui/dialog";
import { EmptyState } from "@/shared/ui/EmptyState";
import { Field, FieldLabel } from "@/shared/ui/field";
import { FullPageSpinner } from "@/shared/ui/FullPageSpinner";
import { Markdown } from "@/shared/ui/Markdown";
import { Textarea } from "@/shared/ui/textarea";
import { type ClubEvent, useCancelEvent, useEvent } from "./api";
import { Attendees } from "./Attendees";
import { EventDialog } from "./EventDialog";
import { RsvpButtons } from "./RsvpButtons";

/** "23 Oct 2026, 18:00 – 21:00", or both dates when it spans days (Lisbon time). */
function when(event: ClubEvent): string {
  if (!event.startsAt || !event.endsAt) return "";
  const sameDay = formatDate(event.startsAt) === formatDate(event.endsAt);
  return `${formatDateTime(event.startsAt)} – ${sameDay ? formatTime(event.endsAt) : formatDateTime(event.endsAt)}`;
}

/** /events/:eventId (08-frontend §4): an event for whoever can read its space, with the answers and who is coming. */
export function EventPage() {
  const { eventId = "" } = useParams();
  const { data: event, error, isPending } = useEvent(eventId);
  const [editing, setEditing] = useState(false);
  const [cancelling, setCancelling] = useState(false);

  if (isPending) return <FullPageSpinner />;
  if (error) {
    if (error instanceof ApiError && error.status === 403) {
      return <EmptyState icon={LockIcon} title="You cannot see this event" description="It belongs to a space you cannot read." />;
    }
    if (error instanceof ApiError && error.status === 404) return <EmptyState icon={SearchXIcon} title="Event not found" />;
    throw error;
  }

  const cancelled = event.status === "CANCELLED";
  return (
    <article className="mx-auto max-w-2xl space-y-6">
      {event.cover && <MediaView media={event.cover} />}
      <header className="space-y-2">
        <Link to={`/c/${event.clubSlug}`} className="text-sm font-medium text-primary hover:underline">{event.clubName}</Link>
        <div className="flex flex-wrap items-center gap-2">
          <h1 className={cn("text-2xl font-semibold tracking-tight", cancelled && "text-muted-foreground line-through")}>
            {event.title}
          </h1>
          {cancelled && <Badge variant="destructive">Cancelled</Badge>}
        </div>
      </header>
      {cancelled && (
        <div role="status" className="rounded-xl border border-destructive/40 bg-destructive/5 p-4 text-sm">
          <p className="font-medium">This event was cancelled</p>
          {event.cancelReason && <p className="text-muted-foreground">{event.cancelReason}</p>}
        </div>
      )}
      <dl className="space-y-2 text-sm">
        <div className="flex items-center gap-2"><dt><CalendarClockIcon className="size-4" aria-label="When" /></dt><dd>{when(event)}</dd></div>
        {event.location && <div className="flex items-center gap-2"><dt><MapPinIcon className="size-4" aria-label="Where" /></dt><dd>{event.location}</dd></div>}
        {event.onlineUrl && (
          <div className="flex items-center gap-2">
            <dt><LinkIcon className="size-4" aria-label="Online" /></dt>
            <dd><a href={event.onlineUrl} target="_blank" rel="noreferrer" className="text-primary hover:underline">{event.onlineUrl}</a></dd>
          </div>
        )}
      </dl>
      <RsvpButtons event={event} />
      {event.description && <Markdown>{event.description}</Markdown>}
      <Attendees eventId={event.id ?? ""} />
      {event.canManage && (
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => setEditing(true)}>Edit</Button>
          <Button variant="destructive" onClick={() => setCancelling(true)}>Cancel event</Button>
        </div>
      )}
      <EventDialog clubId={event.clubId ?? ""} event={event} open={editing} onOpenChange={setEditing} />
      <CancelDialog event={event} open={cancelling} onClose={() => setCancelling(false)} />
    </article>
  );
}

function CancelDialog({ event, open, onClose }: { event: ClubEvent; open: boolean; onClose: () => void }) {
  const cancel = useCancelEvent(event.id ?? "");
  const [reason, setReason] = useState("");

  function onConfirm() {
    cancel.mutate(reason.trim(), {
      onSuccess: () => {
        toast.success("Event cancelled.");
        onClose();
      },
      onError: (error) => toast.error(error.message),
    });
  }

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!next) onClose(); }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Cancel {event.title}?</DialogTitle>
          <DialogDescription>The event stays visible, marked as cancelled. This cannot be undone.</DialogDescription>
        </DialogHeader>
        <Field>
          <FieldLabel htmlFor="cancel-reason">Reason (optional)</FieldLabel>
          <Textarea id="cancel-reason" rows={3} value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>Keep it</Button>
          <Button variant="destructive" disabled={cancel.isPending} onClick={onConfirm}>Cancel event</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
