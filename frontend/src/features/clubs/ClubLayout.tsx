import { SearchXIcon } from "lucide-react";
import { Link, NavLink, Outlet, useParams } from "react-router";
import { useMe } from "@/features/auth";
import { JoinButton, type MembershipView, useMyMembership } from "@/features/membership";
import { SpaceSidebar } from "@/features/spaces";
import { ApiError } from "@/shared/api/errors";
import { cn } from "@/shared/lib/utils";
import { buttonVariants } from "@/shared/ui/button";
import { ClubAvatar } from "@/shared/ui/ClubAvatar";
import { EmptyState } from "@/shared/ui/EmptyState";
import { FullPageSpinner } from "@/shared/ui/FullPageSpinner";
import { type ClubStatus, StatusBadge } from "@/shared/ui/StatusBadge";
import { type Club, useClub } from "./api";
import { ClubAboutPanel } from "./ClubAboutPanel";
import { visibleSettingsTabs } from "./settingsTabs";

type Viewer = { membership: MembershipView | undefined; admin: boolean };

const SECTIONS: { path: string; label: string; visible?: (viewer: Viewer) => boolean }[] = [
  { path: "", label: "Home" },
  { path: "events", label: "Events" },
  { path: "members", label: "Members", visible: ({ membership, admin }) => !!membership?.member || admin },
  { path: "history", label: "History" },
  { path: "settings", label: "Settings", visible: ({ membership }) => visibleSettingsTabs(membership).length > 0 },
];

const sectionClass = ({ isActive }: { isActive: boolean }) => cn(
  "shrink-0 rounded-md px-3 py-1.5 text-sm font-medium transition-colors hover:bg-muted",
  isActive ? "bg-muted text-foreground" : "text-muted-foreground",
);

/**
 * Frame of every /c/:slug page (08-frontend §9.2): header, then sidebar · page · about panel
 * (stacked on phones). Loads the club once; pages read it with useCurrentClub().
 */
export function ClubLayout() {
  const { slug = "" } = useParams();
  const { data: club, error, isPending } = useClub(slug);
  const { data: me } = useMe();
  const { data: membership } = useMyMembership(club?.id ?? "", !!club?.id);

  if (isPending) return <FullPageSpinner />;
  if (error) {
    if (error instanceof ApiError && error.status === 404) {
      return (
        <EmptyState
          icon={SearchXIcon}
          title="Club not found"
          description="This club does not exist, or it is still waiting for approval."
          action={<Link to="/clubs" className={buttonVariants({ variant: "outline" })}>Browse clubs</Link>}
        />
      );
    }
    throw error;                                   // the route's error page takes over
  }

  const open = club.status === "ACTIVE" || club.status === "ARCHIVED";
  return (
    <div className="space-y-6">
      <ClubHeader club={club} />
      {!open && <ReviewNotice club={club} />}
      <div className={cn("grid gap-6", open && "md:grid-cols-[12rem_minmax(0,1fr)] lg:grid-cols-[12rem_minmax(0,1fr)_16rem]")}>
        {open && (
          <aside aria-label="Club sections">
            <nav className="flex gap-1 overflow-x-auto md:flex-col">
              {SECTIONS.filter(({ visible }) => !visible || visible({ membership, admin: !!me?.admin })).map(({ path, label }) => (
                <NavLink key={label} to={path ? `/c/${slug}/${path}` : `/c/${slug}`} end={!path} className={sectionClass}>
                  {label}
                </NavLink>
              ))}
            </nav>
            <SpaceSidebar clubId={club.id ?? ""} clubSlug={slug} />
          </aside>
        )}
        <section className="min-w-0">
          <Outlet context={club} />
        </section>
        {open && <ClubAboutPanel club={club} />}
      </div>
    </div>
  );
}

function ClubHeader({ club }: { club: Club }) {
  return (
    <header className="overflow-hidden rounded-xl border bg-card">
      {club.bannerUrl
        ? <img src={club.bannerUrl} alt="" className="h-32 w-full object-cover sm:h-44" />
        : <div className="h-32 bg-linear-to-r from-primary/25 via-primary/10 to-muted sm:h-44" aria-hidden="true" />}
      <div className="flex flex-wrap items-end gap-4 px-4 pb-4 sm:px-6">
        <div className="-mt-5 self-start rounded-full bg-card p-1">
          <ClubAvatar name={club.name ?? ""} url={club.avatarUrl} size="lg" />
        </div>
        <div className="min-w-0 flex-1 space-y-1 pt-3">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="truncate text-2xl font-semibold tracking-tight">{club.name}</h1>
            <StatusBadge status={club.status as ClubStatus} />
          </div>
          {club.tagline && <p className="text-muted-foreground">{club.tagline}</p>}
        </div>
        <JoinButton club={{ id: club.id ?? "", name: club.name ?? "", status: club.status ?? "", joinPolicy: club.joinPolicy ?? "" }} />
      </div>
    </header>
  );
}

/** A club that is not open yet: only its founder and the admins can see this page. */
function ReviewNotice({ club }: { club: Club }) {
  const rejected = club.status === "REJECTED";
  return (
    <div role="status" className={cn("rounded-xl border p-4 text-sm", rejected ? "border-destructive/40 bg-destructive/5" : "bg-muted/50")}>
      <p className="font-medium">{rejected ? "This request was rejected" : "Waiting for approval"}</p>
      <p className="text-muted-foreground">
        {rejected
          ? club.reviewNote ?? "No reason was given."
          : "An admin will review this request. Until then only you and the admins can see this page."}
      </p>
    </div>
  );
}
