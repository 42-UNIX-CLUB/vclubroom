import { ArchiveIcon, CrownIcon, FlagIcon, type LucideIcon, RotateCcwIcon, SendIcon, UserMinusIcon } from "lucide-react";
import type { ReactNode } from "react";
import { formatDate, formatDateTime } from "@/shared/lib/dates";
import { Button } from "@/shared/ui/button";
import { Skeleton } from "@/shared/ui/skeleton";
import { type TimelineEntry, useTimeline } from "./api";

const name = (person: TimelineEntry["actor"]) => <span className="font-medium">{person?.displayName ?? "Someone"}</span>;

/** One sentence per fact (history.md §4). The reason in data tells how an owner came or went. */
function describe(entry: TimelineEntry): { icon: LucideIcon; text: ReactNode } {
  const reason = typeof entry.data?.reason === "string" ? entry.data.reason : "";
  switch (entry.type) {
    case "CREATION_REQUESTED":
      return { icon: SendIcon, text: <>{name(entry.subject)} asked to create the club</> };
    case "FOUNDED":
      return { icon: FlagIcon, text: <>Founded by {name(entry.subject)}: era {entry.era} begins</> };
    case "OWNER_ADDED":
      return {
        icon: CrownIcon,
        text: reason === "INVITATION" ? <>{name(entry.subject)} became an owner, invited by {name(entry.actor)}</>
          : reason === "ADMIN" ? <>{name(entry.subject)} was made an owner by an admin</>
          : <>{name(entry.subject)} became an owner</>,
      };
    case "OWNER_STEPPED_DOWN":
      return { icon: UserMinusIcon, text: <>{name(entry.subject)} stepped down as owner</> };
    case "OWNER_REMOVED":
      return {
        icon: UserMinusIcon,
        text: reason === "REVIVAL" ? <>{name(entry.subject)} stopped being an owner when the club was revived</>
          : <>{name(entry.subject)} was removed as owner by an admin</>,
      };
    case "ARCHIVED":
      return { icon: ArchiveIcon, text: <>Archived{reason && <>: {reason}</>}. Era {entry.era} ends</> };
    case "REVIVED":
      return { icon: RotateCcwIcon, text: <>Revived by {name(entry.subject)}: era {entry.era} begins</> };
    default:
      return { icon: FlagIcon, text: <>{entry.type}</> };
  }
}

/** The club's lifecycle, newest first (history module). */
export function Timeline({ clubId }: { clubId: string }) {
  const { data, isPending, hasNextPage, fetchNextPage, isFetchingNextPage } = useTimeline(clubId);
  const entries = data?.pages.flatMap((page) => page.items ?? []) ?? [];

  if (isPending) return <Skeleton className="h-24" aria-hidden="true" />;
  return (
    <div className="space-y-2">
      <ol aria-label="Timeline" className="space-y-3 border-l pl-4">
        {entries.map((entry) => {
          const { icon: Icon, text } = describe(entry);
          return (
            <li key={entry.id} className="relative text-sm">
              <span className="absolute top-0.5 -left-[1.6rem] flex size-5 items-center justify-center rounded-full border bg-background">
                <Icon className="size-3 text-muted-foreground" aria-hidden="true" />
              </span>
              <p>{text}</p>
              {entry.occurredAt && (
                <time dateTime={entry.occurredAt} title={formatDateTime(entry.occurredAt)} className="text-xs text-muted-foreground">
                  {formatDate(entry.occurredAt)}
                </time>
              )}
            </li>
          );
        })}
      </ol>
      {hasNextPage && (
        <Button variant="ghost" size="sm" disabled={isFetchingNextPage} onClick={() => void fetchNextPage()}>
          {isFetchingNextPage ? "Loading…" : "Older events"}
        </Button>
      )}
    </div>
  );
}
