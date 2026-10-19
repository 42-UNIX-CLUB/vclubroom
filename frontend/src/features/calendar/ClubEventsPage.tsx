import { PlusIcon } from "lucide-react";
import { useState } from "react";
import { useOutletContext } from "react-router";
import { can, useMyMembership } from "@/features/membership";
import { Button } from "@/shared/ui/button";
import { type CalendarRange, useClubCalendar } from "./api";
import { LazyCalendarView } from "./LazyCalendarView";
import { EventDialog } from "./EventDialog";

/**
 * /c/:slug/events: the club's calendar (the events of spaces I can read), and New event for event managers.
 * The club comes from the frame's outlet context, like the membership pages.
 */
export function ClubEventsPage() {
  const club = useOutletContext<{ id?: string; status?: string }>();
  const { data: membership } = useMyMembership(club.id ?? "");
  const [open, setOpen] = useState(false);
  const [range, setRange] = useState<CalendarRange | null>(null);
  const { data: items = [] } = useClubCalendar(club.id ?? "", range);
  const canSchedule = club.status === "ACTIVE" && can(membership, "EVENT_MANAGE");

  return (
    <section className="space-y-4">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-lg font-semibold">Events</h2>
        {canSchedule && <Button onClick={() => setOpen(true)}><PlusIcon /> New event</Button>}
      </div>
      <LazyCalendarView items={items} showClub={false} onRangeChange={setRange} />
      <EventDialog clubId={club.id ?? ""} open={open} onOpenChange={setOpen} />
    </section>
  );
}
