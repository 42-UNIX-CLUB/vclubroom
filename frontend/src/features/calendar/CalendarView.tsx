import type { DatesSetArg, EventClickArg, EventInput } from "@fullcalendar/core";
import dayGridPlugin from "@fullcalendar/daygrid";
import listPlugin from "@fullcalendar/list";
import FullCalendar from "@fullcalendar/react";
import timeGridPlugin from "@fullcalendar/timegrid";
import { useNavigate } from "react-router";
import type { CalendarItem, CalendarRange } from "./api";
import "./calendar.css";

/**
 * Month, week and list views (08-frontend §11.3). FullCalendar reports the visible range; the page fetches it.
 * Phones start on the list, which reads better than a month grid at 390 px. Events you are going to carry a check.
 */
export function CalendarView({ items, showClub, onRangeChange }: {
  items: CalendarItem[];
  showClub: boolean;
  onRangeChange: (range: CalendarRange) => void;
}) {
  const navigate = useNavigate();
  const narrow = typeof window !== "undefined" && window.innerWidth < 640;
  const events: EventInput[] = items.map((item) => ({
    id: item.id,
    title: showClub ? `${item.clubName} · ${item.title}` : item.title,
    start: item.start,
    end: item.end,
    classNames: [...(item.status === "CANCELLED" ? ["event-cancelled"] : []), ...(item.myRsvp === "GOING" ? ["event-going"] : [])],
  }));

  return (
    <FullCalendar
      plugins={[dayGridPlugin, timeGridPlugin, listPlugin]}
      initialView={narrow ? "listMonth" : "dayGridMonth"}
      headerToolbar={{ left: "prev,next today", center: "title", right: narrow ? "dayGridMonth,listMonth" : "dayGridMonth,timeGridWeek,listMonth" }}
      buttonText={{ today: "Today", month: "Month", week: "Week", list: "List" }}
      timeZone="local"
      firstDay={1}
      height="auto"
      eventTimeFormat={{ hour: "2-digit", minute: "2-digit", hour12: false }}
      noEventsText="No events in this period."
      events={events}
      datesSet={(arg: DatesSetArg) => onRangeChange({ from: arg.startStr, to: arg.endStr })}
      eventClick={(arg: EventClickArg) => { void navigate(`/events/${arg.event.id}`); }}
    />
  );
}
