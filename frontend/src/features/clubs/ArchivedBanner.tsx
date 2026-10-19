import { ArchiveIcon } from "lucide-react";
import { formatDate } from "@/shared/lib/dates";
import type { Club } from "./api";

/**
 * Above every page of an archived club (08-frontend §9.2): since when, why, and what that means.
 * E14-S02 adds "Request a revival".
 */
export function ArchivedBanner({ club }: { club: Club }) {
  const archivedAt = club.eras?.at(-1)?.endedAt;
  const reason = club.reviewNote?.trim().replace(/\.+$/, "");
  return (
    <div role="status" className="flex gap-3 rounded-xl border bg-muted/50 p-4 text-sm">
      <ArchiveIcon className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
      <div className="space-y-1">
        <p className="font-medium">This club is archived{archivedAt && ` since ${formatDate(archivedAt)}`}</p>
        {reason && <p>{reason}.</p>}
        <p className="text-muted-foreground">Everything stays readable, but nothing new can be posted, and nobody can join.</p>
      </div>
    </div>
  );
}
