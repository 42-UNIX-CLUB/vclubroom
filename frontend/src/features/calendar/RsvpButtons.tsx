import { CheckIcon, StarIcon, XIcon } from "lucide-react";
import { toast } from "sonner";
import { cn } from "@/shared/lib/utils";
import { Button } from "@/shared/ui/button";
import { type ClubEvent, type RsvpStatus, useRsvp } from "./api";

const CHOICES: { status: RsvpStatus; label: string; icon: typeof CheckIcon }[] = [
  { status: "GOING", label: "Going", icon: CheckIcon },
  { status: "INTERESTED", label: "Interested", icon: StarIcon },
  { status: "NOT_GOING", label: "Not going", icon: XIcon },
];

/**
 * Going / Interested / Not going (BR-22), with the counts. Pressing your current answer takes it back. Closed once
 * the event has ended or was cancelled (the server says so in canRsvp).
 */
export function RsvpButtons({ event }: { event: ClubEvent }) {
  const rsvp = useRsvp(event.id ?? "");
  const counts: Partial<Record<RsvpStatus, number>> = { GOING: event.going ?? 0, INTERESTED: event.interested ?? 0 };

  function answer(status: RsvpStatus) {
    rsvp.mutate(event.myRsvp === status ? null : status, { onError: (error) => toast.error(error.message) });
  }

  if (!event.canRsvp) {
    return (
      <p className="text-sm text-muted-foreground">
        {event.status === "CANCELLED" ? "Answers are closed." : "This event is over."} {event.going ?? 0} went, {event.interested ?? 0} were interested.
      </p>
    );
  }
  return (
    <div role="group" aria-label="Your answer" className="flex flex-wrap gap-2">
      {CHOICES.map(({ status, label, icon: Icon }) => {
        const pressed = event.myRsvp === status;
        return (
          <Button key={status} variant={pressed ? "default" : "outline"} aria-pressed={pressed} disabled={rsvp.isPending}
            aria-label={counts[status] === undefined ? label : `${label}, ${counts[status]}`}
            onClick={() => answer(status)} className={cn(pressed && "shadow-sm")}>
            <Icon aria-hidden="true" />
            {label}
            {counts[status] !== undefined && <span className="tabular-nums opacity-80">{counts[status]}</span>}
          </Button>
        );
      })}
    </div>
  );
}
