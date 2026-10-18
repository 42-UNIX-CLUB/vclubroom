import { InboxIcon } from "lucide-react";
import { formatRelative } from "@/shared/lib/dates";
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card";
import { EmptyState } from "@/shared/ui/EmptyState";
import { Skeleton } from "@/shared/ui/skeleton";
import { StatusBadge, type ClubStatus } from "@/shared/ui/StatusBadge";
import { useMyClubRequests } from "./api";

/** "My requests": pending and rejected club requests of the logged-in user (E04-S01). */
export function MyClubRequests() {
  const { data: requests, isPending, isError } = useMyClubRequests();
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">My requests</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {isPending && <Skeleton className="h-16" />}
        {isError && <p className="text-sm text-destructive">Could not load your requests.</p>}
        {requests?.length === 0 && (
          <EmptyState icon={InboxIcon} title="No requests yet" description="Clubs you ask for appear here until an admin decides." />
        )}
        {requests?.map((club) => (
          <div key={club.id} className="space-y-1 rounded-lg border p-3">
            <div className="flex items-center justify-between gap-2">
              <span className="font-medium">{club.name}</span>
              <StatusBadge status={club.status as ClubStatus} />
            </div>
            {club.createdAt && <p className="text-xs text-muted-foreground">Requested {formatRelative(club.createdAt)}</p>}
            {club.status === "REJECTED" && club.reviewNote && (
              <p className="text-sm text-muted-foreground">Reason: {club.reviewNote}</p>
            )}
          </div>
        ))}
      </CardContent>
    </Card>
  );
}