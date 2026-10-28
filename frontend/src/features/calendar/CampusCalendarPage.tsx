import { useState } from "react";
import { PageHeader } from "@/shared/ui/PageHeader";
import { Tabs, TabsList, TabsTrigger } from "@/shared/ui/tabs";
import { type CalendarRange, useCampusCalendar } from "./api";
import { CalendarFeedButton } from "./CalendarFeedPanel";
import { LazyCalendarView } from "./LazyCalendarView";

/** /calendar: every event I can see on campus, or only my clubs' (calendar.md §8.2), and my feed for calendar apps (E11-S05). */
export function CampusCalendarPage() {
  const [scope, setScope] = useState<"all" | "mine">("all");
  const [range, setRange] = useState<CalendarRange | null>(null);
  const { data: items = [] } = useCampusCalendar(scope, range);

  return (
    <>
      <PageHeader title="Campus calendar" actions={<CalendarFeedButton />} />
      <Tabs value={scope} onValueChange={(value) => setScope(value as "all" | "mine")} className="mb-4">
        <TabsList>
          <TabsTrigger value="all">All events</TabsTrigger>
          <TabsTrigger value="mine">My clubs</TabsTrigger>
        </TabsList>
      </Tabs>
      <LazyCalendarView items={items} showClub onRangeChange={setRange} />
    </>
  );
}
