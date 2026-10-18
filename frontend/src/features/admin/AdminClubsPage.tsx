import { InboxIcon } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router";
import { formatDate } from "@/shared/lib/dates";
import { Card, CardContent } from "@/shared/ui/card";
import { EmptyState } from "@/shared/ui/EmptyState";
import { PageHeader } from "@/shared/ui/PageHeader";
import { Skeleton } from "@/shared/ui/skeleton";
import { StatusBadge } from "@/shared/ui/StatusBadge";
import { Tabs, TabsList, TabsTrigger } from "@/shared/ui/tabs";
import { type ClubStatusFilter, useAdminClubs } from "./api";

const STATUSES: { value: ClubStatusFilter; label: string }[] = [
  { value: "ACTIVE", label: "Active" },
  { value: "PENDING_APPROVAL", label: "Pending" },
  { value: "ARCHIVED", label: "Archived" },
  { value: "REJECTED", label: "Rejected" },
];

/** /admin/clubs (E15-S01): every club by status. E14-S01 adds the archive action to active clubs. */
export function AdminClubsPage() {
  const [status, setStatus] = useState<ClubStatusFilter>("ACTIVE");
  const { data, isPending } = useAdminClubs(status);

  return (
    <>
      <PageHeader title="All clubs" />
      <Tabs value={status} onValueChange={(value) => setStatus(value as ClubStatusFilter)} className="mb-4">
        <TabsList>
          {STATUSES.map(({ value, label }) => <TabsTrigger key={value} value={value}>{label}</TabsTrigger>)}
        </TabsList>
      </Tabs>
      {isPending && <Skeleton className="h-24" />}
      {data?.items?.length === 0 && <EmptyState icon={InboxIcon} title="No clubs with this status" />}
      <div className="space-y-2">
        {data?.items?.map((club) => (
          <Card key={club.id}>
            <CardContent className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <Link to={`/c/${club.slug}`} className="font-medium hover:underline">{club.name}</Link>
                {club.createdAt && <p className="text-xs text-muted-foreground">Created {formatDate(club.createdAt)}</p>}
              </div>
              <StatusBadge status={club.status as ClubStatusFilter} />
            </CardContent>
          </Card>
        ))}
      </div>
    </>
  );
}
