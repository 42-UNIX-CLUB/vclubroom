import { ActivityIcon, CircleAlertIcon, CircleCheckIcon } from "lucide-react";
import { formatRelative } from "@/shared/lib/dates";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/ui/card";
import { useOps } from "./api";

/** A listener whose oldest unfinished event is older than this is shown as stuck. */
const STUCK_AFTER_MS = 5 * 60_000;

/**
 * The admin home's health card (E15-S04): events listeners haven't finished (06-messaging §8) and the email outbox
 * (R-27). Healthy, it says so in one line. Emails being retried are worth a look, not an alarm; a stuck listener or an
 * email given up after 5 attempts is late, and the card says which, with the last email error.
 */
export function OpsCard() {
  const { data, isError } = useOps();
  const outbox = data?.outbox ?? [];
  const emails = data?.emails;
  const now = Date.now();
  const stuck = outbox.filter((l) => l.oldest && now - new Date(l.oldest).getTime() > STUCK_AFTER_MS);
  const pendingEvents = outbox.reduce((sum, l) => sum + (l.count ?? 0), 0);
  const late = stuck.length > 0 || !!emails?.failed;
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
        <CardContent className="grid gap-4 text-sm sm:grid-cols-2">
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
        </CardContent>
      )}
    </Card>
  );
}
