import { LockIcon } from "lucide-react";
import { Navigate, NavLink, Outlet } from "react-router";
import { can, useJoinRequests, useMyMembership } from "@/features/membership";
import { cn } from "@/shared/lib/utils";
import { Badge } from "@/shared/ui/badge";
import { EmptyState } from "@/shared/ui/EmptyState";
import { FullPageSpinner } from "@/shared/ui/FullPageSpinner";
import { PageHeader } from "@/shared/ui/PageHeader";
import { useCurrentClub } from "./currentClub";
import { visibleSettingsTabs } from "./settingsTabs";

const tabClass = ({ isActive }: { isActive: boolean }) => cn(
  "-mb-px flex shrink-0 items-center gap-2 border-b-2 px-3 py-2 text-sm font-medium transition-colors",
  isActive ? "border-primary text-foreground" : "border-transparent text-muted-foreground hover:text-foreground",
);

/** /c/:slug/settings/*: one tab per permission the viewer holds, the open tab below. */
export function ClubSettingsLayout() {
  const club = useCurrentClub();
  const { data: membership } = useMyMembership(club.id ?? "");
  const { data: requests } = useJoinRequests(club.id ?? "", can(membership, "MEMBER_APPROVE"));

  if (!membership) return <FullPageSpinner />;
  const tabs = visibleSettingsTabs(membership);
  if (tabs.length === 0) {
    return <EmptyState icon={LockIcon} title="Nothing to manage here" description="Club settings are for owners and members with a management role." />;
  }
  const pending = requests?.length ?? 0;
  return (
    <div className="space-y-6">
      <PageHeader level={2} title="Club settings" />
      <nav aria-label="Club settings" className="flex overflow-x-auto border-b">
        {tabs.map(({ path, label }) => (
          <NavLink key={path} to={path} className={tabClass}>
            {label}
            {path === "requests" && pending > 0 && <Badge aria-label={`${pending} pending`}>{pending}</Badge>}
          </NavLink>
        ))}
      </nav>
      <Outlet context={club} />
    </div>
  );
}

/** /c/:slug/settings: opens the first tab the viewer may use (the layout handles "none"). */
export function ClubSettingsIndex() {
  const club = useCurrentClub();
  const { data: membership } = useMyMembership(club.id ?? "");
  const first = visibleSettingsTabs(membership)[0];
  return first ? <Navigate to={first.path} replace /> : null;
}
