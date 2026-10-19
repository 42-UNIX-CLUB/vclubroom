import { Link } from "react-router";
import { formatDateTime } from "@/shared/lib/dates";
import { Skeleton } from "@/shared/ui/skeleton";
import { useClubCalendar } from "./api";

const DAY = 86_400_000;

/** The club's next three scheduled events, for the club page's side panel. */
export function UpcomingEvents({ clubId }: { clubId: string }) {
  // Rounded to the hour, so the query key stays the same while the page is open.
  const from = new Date(Math.floor(Date.now() / 3_600_000) * 3_600_000);
  const { data, isPending } = useClubCalendar(clubId, { from: from.toISOString(), to: new Date(from.getTime() + 62 * DAY).toISOString() });
  const next = (data ?? []).filter((item) => item.status === "SCHEDULED").slice(0, 3);

  if (isPending) return <Skeleton className="h-10" aria-hidden="true" />;
  if (next.length === 0) return <p className="text-sm text-muted-foreground">Nothing planned yet.</p>;
  return (
    <ul className="space-y-2 text-sm">
      {next.map((item) => (
        <li key={item.id}>
          <Link to={`/events/${item.id}`} className="font-medium hover:underline">{item.title}</Link>
          {item.start && <p className="text-xs text-muted-foreground">{formatDateTime(item.start)}</p>}
        </li>
      ))}
    </ul>
  );
}
