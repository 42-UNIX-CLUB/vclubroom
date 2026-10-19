// src/features/calendar/CampusCalendarPage.tsx
import type { DatesSetArg, EventClickArg, EventInput } from "@fullcalendar/core";
import dayGridPlugin from "@fullcalendar/daygrid";
import interactionPlugin from "@fullcalendar/interaction";
import listPlugin from "@fullcalendar/list";
import FullCalendar from "@fullcalendar/react";
import timeGridPlugin from "@fullcalendar/timegrid";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useNavigate } from "react-router";
// import { api } from "@/shared/api/client";
import { mockCalendar } from "./mockCalendar";

// const USE_MOCK = true; // flip to false when the backend exists

export function CampusCalendar({ scope }: { scope: "all" | "mine" }) {
  const [range, setRange] = useState<{ from: string; to: string } | null>(null);
  const navigate = useNavigate();

  const { data = [] } = useQuery({
    queryKey: ["calendar", scope, range],
    enabled: range !== null,
    queryFn: async () => mockCalendar(range!.from, range!.to, scope),
    // queryFn: async () => {
    //   if (USE_MOCK) return mockCalendar(range!.from, range!.to, scope);
    //
    //   const { data } = await api.GET("/api/calendar", {
    //     params: { query: { from: range!.from, to: range!.to, scope } },
    //   });
    //   return data ?? [];
    // },
  });

  const events: EventInput[] = data.map((item) => ({
    id: item.id,
    title: `${item.clubName} · ${item.title}`,
    start: item.start,
    end: item.end,
    classNames: item.status === "CANCELLED" ? ["line-through", "opacity-60"] : [],
  }));

  return (
    <FullCalendar
      plugins={[dayGridPlugin, timeGridPlugin, listPlugin, interactionPlugin]}
      initialView="dayGridMonth"
      headerToolbar={{ left: "prev,next today", center: "title", right: "dayGridMonth,timeGridWeek,listWeek" }}
      timeZone="local"
      firstDay={1}
      height="auto"
      events={events}
      datesSet={(arg: DatesSetArg) => setRange({ from: arg.startStr, to: arg.endStr })}
      eventClick={(arg: EventClickArg) => navigate(`/events/${arg.event.id}`)}
    />
  );
}

export function CampusCalendarPage() {
  const [scope, setScope] = useState<"all" | "mine">("all");

  return (
    <div className="space-y-4">
      <div className="flex gap-2">
        <button onClick={() => setScope("all")} disabled={scope === "all"}>
          All
        </button>
        <button onClick={() => setScope("mine")} disabled={scope === "mine"}>
          Mine
        </button>
      </div>
      <CampusCalendar scope={scope} />
    </div>
  );
}
