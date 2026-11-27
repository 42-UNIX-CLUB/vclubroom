import { formatTime } from "@/shared/lib/dates";
import { cn } from "@/shared/lib/utils";
import { PageHeader } from "@/shared/ui/PageHeader";
import { Skeleton } from "@/shared/ui/skeleton";
import { type ServiceState, usePublicStatus } from "./api";

const WORDS: Record<ServiceState, string> = { OPERATIONAL: "Operational", DEGRADED: "Degraded", DOWN: "Down" };
const SUMMARY: Record<ServiceState, string> = {
  OPERATIONAL: "Everything is working.",
  DEGRADED: "Some services have problems: the rest works.",
  DOWN: "Virtual Clubroom is down. The team is on it.",
};
const DOT: Record<ServiceState, string> = { OPERATIONAL: "bg-primary", DEGRADED: "bg-amber-500", DOWN: "bg-destructive" };

/** /status (E19-S02): each service and the platform as a whole, for everyone. */
export function StatusPage() {
  const { data, isPending, isError } = usePublicStatus();
  return (
    <>
      <PageHeader title="Status" description="Whether each part of Virtual Clubroom works. Checked again every 30 seconds." />
      <div className="max-w-2xl space-y-4">
        {isPending && <Skeleton className="h-48" />}
        {isError && <p role="alert" className="rounded-lg border border-destructive/40 p-4 text-sm">Virtual Clubroom isn't answering. It may be down, or your connection may be.</p>}
        {data?.status && (
          <>
            <p role="status" className={cn("rounded-lg border p-4 font-medium", data.status !== "OPERATIONAL" && "border-destructive/40")}>
              {SUMMARY[data.status]}
            </p>
            <ul className="divide-y rounded-lg border">
              {data.services?.map((s) => (
                <li key={s.id} className="flex items-center justify-between gap-2 p-3 text-sm">
                  <span>{s.name}</span>
                  <span className="flex items-center gap-2 text-muted-foreground">
                    <span className={cn("size-2 rounded-full", s.status && DOT[s.status])} aria-hidden="true" />
                    {s.status && WORDS[s.status]}
                  </span>
                </li>
              ))}
            </ul>
            {data.checkedAt && <p className="text-xs text-muted-foreground">Checked at {formatTime(data.checkedAt)}.</p>}
          </>
        )}
      </div>
    </>
  );
}
