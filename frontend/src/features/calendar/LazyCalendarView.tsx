import { lazy, Suspense } from "react";
import { Skeleton } from "@/shared/ui/skeleton";
import type { CalendarItem, CalendarRange } from "./api";

// FullCalendar weighs ~270 KB. Loaded only when a calendar is on screen, so the club page (which shows the
// upcoming events from this feature) does not carry it.
const CalendarView = lazy(() => import("./CalendarView").then((m) => ({ default: m.CalendarView })));

export function LazyCalendarView(props: { items: CalendarItem[]; showClub: boolean; onRangeChange: (range: CalendarRange) => void }) {
  return (
    <Suspense fallback={<Skeleton className="h-96" aria-hidden="true" />}>
      <CalendarView {...props} />
    </Suspense>
  );
}
