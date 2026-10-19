import { CalendarDaysIcon, PlusIcon } from "lucide-react";
import { useState } from "react";
import { useOutletContext } from "react-router";
import { can, useMyMembership } from "@/features/membership";
import { Button } from "@/shared/ui/button";
import { EmptyState } from "@/shared/ui/EmptyState";
import { EventDialog } from "./EventDialog";

/**
 * /c/:slug/events: event managers schedule from here. E11-S02 puts the club calendar on this page.
 * The club comes from the frame's outlet context, like the membership pages.
 */
export function ClubEventsPage() {
  const club = useOutletContext<{ id?: string; status?: string }>();
  const { data: membership } = useMyMembership(club.id ?? "");
  const [open, setOpen] = useState(false);
  const canSchedule = club.status === "ACTIVE" && can(membership, "EVENT_MANAGE");

  return (
    <section className="space-y-4">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-lg font-semibold">Events</h2>
        {canSchedule && <Button onClick={() => setOpen(true)}><PlusIcon /> New event</Button>}
      </div>
      <EmptyState icon={CalendarDaysIcon} title="The club calendar is coming" description="It arrives with E11-S02." />
      <EventDialog clubId={club.id ?? ""} open={open} onOpenChange={setOpen} />
    </section>
  );
}
