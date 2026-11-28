import { HistoryIcon, SearchXIcon, UsersIcon } from "lucide-react";
import { Link, useParams } from "react-router";
import { useMe } from "@/features/auth";
import { useMyMembership } from "@/features/membership";
import { ApiError } from "@/shared/api/errors";
import { formatDate } from "@/shared/lib/dates";
import { usePageTitle } from "@/shared/lib/usePageTitle";
import { Button, buttonVariants } from "@/shared/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card";
import { ClubAvatar } from "@/shared/ui/ClubAvatar";
import { EmptyState } from "@/shared/ui/EmptyState";
import { FullPageSpinner } from "@/shared/ui/FullPageSpinner";
import { Markdown } from "@/shared/ui/Markdown";
import { type ClubStatus, StatusBadge } from "@/shared/ui/StatusBadge";
import { type PublicClub, usePublicClub } from "./api";

/** /clubs/:slug/about, public (R-24): what a visitor may know about a club, and the way in. */
export function PublicClubProfilePage() {
  const { slug = "" } = useParams();
  const { data: club, error, isPending } = usePublicClub(slug);
  usePageTitle(club?.name);

  if (isPending) return <FullPageSpinner />;
  if (error) {
    if (error instanceof ApiError && error.status === 404) {
      return (
        <EmptyState
          icon={SearchXIcon}
          title="Club not found"
          description="There is no public club at this address."
          action={<Link to="/clubs" className={buttonVariants({ variant: "outline" })}>Browse clubs</Link>}
        />
      );
    }
    throw error;
  }

  const members = club.memberCount ?? 0;
  return (
    <div className="mx-auto max-w-3xl space-y-6">
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
              {club.status === "ARCHIVED" && <StatusBadge status={club.status as ClubStatus} />}
            </div>
            {club.tagline && <p className="text-muted-foreground">{club.tagline}</p>}
          </div>
          <div className="flex flex-wrap gap-2">
            <JoinCta club={club} />
          </div>
        </div>
      </header>

      <Card>
        <CardHeader>
          <CardTitle>About</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground">
            <span className="flex items-center gap-1.5"><UsersIcon className="size-4" aria-hidden="true" />{members} {members === 1 ? "member" : "members"}</span>
            <span>{club.category}</span>
            {club.foundedAt && <span>Founded {formatDate(club.foundedAt)}</span>}
            {(club.currentEra ?? 1) > 1 && <span>Era {club.currentEra}</span>}
          </p>
          {club.description
            ? <Markdown>{club.description}</Markdown>
            : <p className="text-sm text-muted-foreground">No description yet.</p>}
          {!!club.tags?.length && (
            <ul className="flex flex-wrap gap-2" aria-label="Tags">
              {club.tags.map((tag) => (
                <li key={tag}>
                  <Link to={`/clubs?tag=${encodeURIComponent(tag)}`} className="rounded-full border px-2.5 py-0.5 text-sm hover:border-primary/50 hover:bg-accent/40">
                    #{tag}
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

/**
 * The way in (08-frontend §9.1). Joining happens on the club page, whose join button runs ?intent=join (E07-S02);
 * visitors log in first and come back to it through returnTo.
 */
function JoinCta({ club }: { club: PublicClub }) {
  const { data: me, isPending: meLoading } = useMe();
  const { data: membership } = useMyMembership(club.clubId ?? "", !!me);
  const clubPage = `/c/${club.slug}`;
  const joinLabel = club.joinPolicy === "OPEN" ? "Join club" : "Request to join";

  if (club.status === "ARCHIVED") {
    return (
      <Link to={`${clubPage}/history`} className={buttonVariants({ variant: "outline" })}>
        <HistoryIcon /> See its history
      </Link>
    );
  }
  // Nothing until we know who is looking: a member must not see "Join" flash before "Open club page".
  if (meLoading || (me && !membership)) return null;
  if (!me) {
    return <Link to={`/login?returnTo=${encodeURIComponent(`${clubPage}?intent=join`)}`} className={buttonVariants()}>{joinLabel}</Link>;
  }
  if (membership?.member) {
    return <Link to={clubPage} className={buttonVariants()}>Open club page</Link>;
  }
  return (
    <>
      {membership?.pendingRequest
        ? <Button variant="secondary" disabled>Request sent</Button>
        : <Link to={`${clubPage}?intent=join`} className={buttonVariants()}>{joinLabel}</Link>}
      <Link to={clubPage} className={buttonVariants({ variant: "outline" })}>Open club page</Link>
    </>
  );
}
