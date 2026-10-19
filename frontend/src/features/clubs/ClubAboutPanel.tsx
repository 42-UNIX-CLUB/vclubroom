import { UpcomingEvents } from "@/features/calendar";
import { useMemberSummary } from "@/features/membership";
import { formatDate } from "@/shared/lib/dates";
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card";
import { ClubAvatar } from "@/shared/ui/ClubAvatar";
import { Skeleton } from "@/shared/ui/skeleton";
import type { Club } from "./api";

/** Right panel of every club page: members, owners, founding date, and the next events. */
export function ClubAboutPanel({ club }: { club: Club }) {
  const { data: summary } = useMemberSummary(club.id ?? "");
  const founded = club.eras?.[0]?.startedAt;
  const members = summary?.memberCount ?? 0;

  return (
    <aside aria-label="About the club" className="space-y-4 md:col-start-2 lg:col-start-auto">
      <Card size="sm">
        <CardHeader>
          <CardTitle>About</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4 text-sm">
          {summary ? <p className="font-medium">{members} {members === 1 ? "member" : "members"}</p> : <Skeleton className="h-5 w-24" />}
          {founded && <p className="text-muted-foreground">Founded {formatDate(founded)}{(club.currentEra ?? 1) > 1 && ` · era ${club.currentEra}`}</p>}
          <div className="space-y-2">
            <p className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">Owners</p>
            <ul className="space-y-2">
              {summary?.owners?.map((owner) => (
                <li key={owner.id} className="flex items-center gap-2">
                  <ClubAvatar name={owner.displayName ?? owner.login ?? ""} url={owner.avatarUrl} size="sm" />
                  <span className="min-w-0 truncate">{owner.displayName}</span>
                  <span className="truncate text-muted-foreground">@{owner.login}</span>
                </li>
              ))}
            </ul>
          </div>
        </CardContent>
      </Card>
      <Card size="sm">
        <CardHeader>
          <CardTitle>Upcoming events</CardTitle>
        </CardHeader>
        <CardContent>
          <UpcomingEvents clubId={club.id ?? ""} />
        </CardContent>
      </Card>
    </aside>
  );
}
