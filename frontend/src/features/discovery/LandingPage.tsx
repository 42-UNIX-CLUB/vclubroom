import { ArrowRightIcon } from "lucide-react";
import { Link } from "react-router";
import { useMe } from "@/features/auth";
import { cn } from "@/shared/lib/utils";
import { buttonVariants } from "@/shared/ui/button";
import { EmptyState } from "@/shared/ui/EmptyState";
import { Skeleton } from "@/shared/ui/skeleton";
import { type ClubCardData, type Landing, useLanding } from "./api";
import { ClubCard } from "./ClubCard";

/** `/`, public (08-frontend §9.1): what is happening on campus, and where to start. */
export function LandingPage() {
  const { data: me } = useMe();
  const { data: landing, isPending, isError } = useLanding();
  const loggedIn = !!me;

  return (
    <div className="space-y-12">
      <Hero loggedIn={loggedIn} />
      {isPending && <LoadingSections />}
      {isError && <EmptyState title="Clubs are not available right now" description="Please try again in a moment." />}
      {landing && ((landing.stats?.activeClubs ?? 0) === 0
        ? <NoClubsYet loggedIn={loggedIn} />
        : (
          <>
            <Stats stats={landing.stats} />
            <ClubSection title="Most active" seeAll="/clubs?sort=active" clubs={landing.mostActive} />
            <ClubSection title="Newest clubs" seeAll="/clubs?sort=new" clubs={landing.newest} />
          </>
        ))}
      {landing && <Categories categories={landing.categories} />}
    </div>
  );
}

function Hero({ loggedIn }: { loggedIn: boolean }) {
  return (
    <section className="overflow-hidden rounded-2xl border bg-linear-to-br from-primary/15 via-accent/50 to-background px-6 py-12 sm:px-10 sm:py-16">
      <h1 className="mt-3 max-w-2xl text-3xl font-semibold tracking-tight text-balance sm:text-5xl">
        Find your people, build things together.
      </h1>
      <p className="mt-4 max-w-xl text-muted-foreground sm:text-lg">
        Discover the clubs on campus, join in one click, and follow their posts, chats and events in one place.
      </p>
      <div className="mt-8 flex flex-wrap gap-3">
        <Link to="/clubs" className={buttonVariants({ size: "lg", className: "px-4" })}>
          Browse clubs <ArrowRightIcon />
        </Link>
        {loggedIn
          ? <Link to="/clubs/new" className={buttonVariants({ variant: "outline", size: "lg", className: "px-4" })}>Start a club</Link>
          : (
            // Full-page navigation: the 42 login leaves the SPA and comes back to "/".
            <a href="/oauth2/authorization/fortytwo" className={buttonVariants({ variant: "outline", size: "lg", className: "px-4" })}>
              Login with 42
            </a>
          )}
      </div>
    </section>
  );
}

function Stats({ stats }: { stats: Landing["stats"] }) {
  const tiles = [
    { label: "Active clubs", value: stats?.activeClubs ?? 0 },
    { label: "Memberships", value: stats?.memberships ?? 0 },
  ];
  return (
    <section aria-label="Campus in numbers" className="grid grid-cols-2 gap-4 sm:max-w-md">
      {tiles.map(({ label, value }) => (
        <div key={label} className="rounded-xl border bg-card p-4">
          <p className="text-3xl font-semibold tabular-nums">{value}</p>
          <p className="text-sm text-muted-foreground">{label}</p>
        </div>
      ))}
    </section>
  );
}

function ClubSection({ title, seeAll, clubs }: { title: string; seeAll: string; clubs?: ClubCardData[] }) {
  if (!clubs?.length) return null;
  return (
    <section className="space-y-4">
      <div className="flex items-baseline justify-between gap-4">
        <h2 className="text-xl font-semibold tracking-tight">{title}</h2>
        <Link to={seeAll} className="text-sm font-medium text-primary hover:underline">See all</Link>
      </div>
      <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {clubs.map((club) => <li key={club.clubId}><ClubCard club={club} /></li>)}
      </ul>
    </section>
  );
}

function Categories({ categories }: { categories: Landing["categories"] }) {
  if (!categories?.length) return null;
  return (
    <section className="space-y-4">
      <h2 className="text-xl font-semibold tracking-tight">Browse by category</h2>
      <ul className="flex flex-wrap gap-2">
        {categories.map(({ name, count }) => (
          <li key={name}>
            <Link
              to={`/clubs?category=${encodeURIComponent(name ?? "")}`}
              className={cn(
                "inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-sm transition-colors hover:border-primary/50 hover:bg-accent/40",
                !count && "text-muted-foreground",
              )}
            >
              {name} <span className="tabular-nums text-muted-foreground">{count}</span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}

function NoClubsYet({ loggedIn }: { loggedIn: boolean }) {
  return (
    <EmptyState
      title="No clubs yet"
      description="Every club starts with one person. Yours could be the first."
      action={
        <Link to={loggedIn ? "/clubs/new" : "/login?returnTo=%2Fclubs%2Fnew"} className={buttonVariants()}>
          Start a club
        </Link>
      }
    />
  );
}

function LoadingSections() {
  return (
    <div className="space-y-4" aria-hidden="true">
      <Skeleton className="h-24 w-full max-w-md" />
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {[0, 1, 2].map((i) => <Skeleton key={i} className="h-36" />)}
      </div>
    </div>
  );
}
