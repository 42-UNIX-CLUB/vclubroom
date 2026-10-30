import { ActivityIcon, CircleAlertIcon, CircleCheckIcon, ExternalLinkIcon } from "lucide-react";
import { formatRelative } from "@/shared/lib/dates";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/ui/card";
import { useOps } from "./api";

/** A listener whose oldest unfinished event is older than this is shown as stuck. */
const STUCK_AFTER_MS = 5 * 60_000;
/** The tracker is late when a ticket waits this long for it, or its changes weren't read for this long (E17-S04). */
const TRACKER_LATE_MS = 10 * 60_000;

/**
 * The admin home's health card (E15-S04): events listeners haven't finished (06-messaging §8), the email outbox (R-27)
 * and the ticket tracker (E17-S04). Healthy, it says so in one line. Emails being retried are worth a look, not an alarm;
 * a stuck listener, an email given up after 5 attempts or a tracker out of reach is late, and the card says which.
 */
export function OpsCard() {
  const { data, isError } = useOps();
  const outbox = data?.outbox ?? [];
  const emails = data?.emails;
  const now = Date.now();
  const stuck = outbox.filter((l) => l.oldest && now - new Date(l.oldest).getTime() > STUCK_AFTER_MS);
  const pendingEvents = outbox.reduce((sum, l) => sum + (l.count ?? 0), 0);
  const tickets = data?.tickets;
  const olderThan = (iso: string | undefined, ms: number) => !!iso && now - new Date(iso).getTime() > ms;
  const trackerLate = olderThan(tickets?.oldestUnsent, TRACKER_LATE_MS) || olderThan(tickets?.lastRead, TRACKER_LATE_MS);
  const waitingForTracker = (tickets?.unsentTickets ?? 0) + (tickets?.unsentReplies ?? 0);
  const late = stuck.length > 0 || !!emails?.failed || trackerLate;
  const retrying = !!emails?.retrying;

  return (
    <Card size="sm" className="mb-6">
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><ActivityIcon className="size-4" aria-hidden="true" /> Platform health</CardTitle>
        <CardDescription className="flex items-center gap-1.5">
          {isError ? <><CircleAlertIcon className="size-4 text-destructive" aria-hidden="true" /> Could not read the health report.</>
            : !data ? "Checking…"
              : late ? <><CircleAlertIcon className="size-4 text-destructive" aria-hidden="true" /> Something is late: see below.</>
                : retrying ? <><CircleAlertIcon className="size-4 text-muted-foreground" aria-hidden="true" /> Some emails are waiting to be retried.</>
                  : <><CircleCheckIcon className="size-4 text-primary" aria-hidden="true" /> All caught up.</>}
        </CardDescription>
      </CardHeader>
      {data && (
        <CardContent className="grid gap-4 text-sm sm:grid-cols-2 lg:grid-cols-3">
          <div>
            <p className="font-medium">Events</p>
            <p className="text-muted-foreground">{pendingEvents === 0 ? "Every listener is up to date." : `${pendingEvents} being handled.`}</p>
            {stuck.length > 0 && (
              <ul aria-label="Stuck listeners" className="mt-1 space-y-0.5">
                {stuck.map((l) => (
                  <li key={`${l.listener}-${l.eventType}`} className="text-destructive">
                    {l.listener} ({l.eventType}): {l.count}, oldest {formatRelative(l.oldest!)}
                  </li>
                ))}
              </ul>
            )}
          </div>
          <div>
            <p className="font-medium">Emails</p>
            <p className="text-muted-foreground">
              {emails?.sentLastDay ?? 0} sent in the last day · {emails?.waiting ?? 0} waiting · {emails?.retrying ?? 0} retrying
            </p>
            {!!emails?.failed && <p className="text-destructive">{emails.failed} given up after 5 attempts.</p>}
            {emails?.lastError && <p className="mt-1 break-words text-xs text-muted-foreground">Last error: {emails.lastError}</p>}
          </div>
          {tickets && (
            <div>
              <p className="font-medium">Tickets</p>
              <p className="text-muted-foreground">
                {waitingForTracker === 0 ? "The tracker has every ticket." : `${waitingForTracker} waiting for the tracker.`}
                {tickets.lastRead && ` Read ${formatRelative(tickets.lastRead)}.`}
              </p>
              {trackerLate && <p className="text-destructive">The tracker hasn't answered for a while.</p>}
              {tickets.lastError && <p className="mt-1 break-words text-xs text-muted-foreground">Last error: {tickets.lastError}</p>}
              {tickets.trackerUrl && (
                <a href={tickets.trackerUrl} target="_blank" rel="noopener noreferrer" className="mt-1 inline-flex items-center gap-1 text-primary underline-offset-4 hover:underline">
                  Open the tracker <ExternalLinkIcon className="size-3.5" aria-hidden="true" />
                </a>
              )}
            </div>
          )}
        </CardContent>
      )}
    </Card>
  );
}
