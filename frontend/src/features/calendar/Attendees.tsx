import { Badge } from "@/shared/ui/badge";
import { ClubAvatar } from "@/shared/ui/ClubAvatar";
import { useAttendees } from "./api";

/** Who is coming: going first, then interested, by name. */
export function Attendees({ eventId }: { eventId: string }) {
  const { data: attendees } = useAttendees(eventId);
  if (!attendees) return null;
  return (
    <section aria-labelledby="attendees-heading" className="space-y-2">
      <h2 id="attendees-heading" className="text-base font-semibold">Who's coming</h2>
      {attendees.length === 0
        ? <p className="text-sm text-muted-foreground">Nobody has answered yet.</p>
        : (
          <ul className="flex flex-wrap gap-2">
            {attendees.map(({ user, status }) => (
              <li key={user?.id} className="flex items-center gap-2 rounded-full border py-1 pr-3 pl-1 text-sm">
                <ClubAvatar name={user?.displayName ?? ""} url={user?.avatarUrl} size="sm" />
                {user?.displayName}
                {status === "INTERESTED" && <Badge variant="secondary">Interested</Badge>}
              </li>
            ))}
          </ul>
        )}
    </section>
  );
}
