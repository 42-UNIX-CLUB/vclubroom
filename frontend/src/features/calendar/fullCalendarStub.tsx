import { useEffect } from "react";

/**
 * Stands in for FullCalendar in tests (jsdom cannot lay out its grid): reports one visible range on mount and
 * renders each event as a button that does what a click on the real one does.
 */
export default function FullCalendarStub({ events = [], datesSet, eventClick }: {
  events?: { id?: string; title?: string; classNames?: string[] }[];
  datesSet?: (arg: { startStr: string; endStr: string }) => void;
  eventClick?: (arg: { event: { id: string } }) => void;
}) {
  useEffect(() => {
    datesSet?.({ startStr: "2026-10-26T00:00:00+00:00", endStr: "2026-12-07T00:00:00+00:00" });
  }, []);                                                   // eslint-disable-line react-hooks/exhaustive-deps
  return (
    <ul aria-label="Calendar">
      {events.map((event) => (
        <li key={event.id}>
          <button type="button" className={event.classNames?.join(" ")} onClick={() => eventClick?.({ event: { id: event.id ?? "" } })}>
            {event.title}
          </button>
        </li>
      ))}
    </ul>
  );
}
