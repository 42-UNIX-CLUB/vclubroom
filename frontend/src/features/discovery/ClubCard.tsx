import { UsersIcon } from "lucide-react";
import { Link } from "react-router";
import { ClubAvatar } from "@/shared/ui/ClubAvatar";
import { StatusBadge } from "@/shared/ui/StatusBadge";
import type { ClubCardData } from "./api";

/** One club in a grid (landing page, directory). The whole card links to the public profile. */
export function ClubCard({ club }: { club: ClubCardData }) {
  const members = club.memberCount ?? 0;
  return (
    <Link
      to={`/clubs/${club.slug}/about`}
      className="group flex h-full flex-col gap-3 rounded-xl border bg-card p-4 text-card-foreground transition-colors outline-none hover:border-primary/50 hover:bg-accent/40 focus-visible:ring-3 focus-visible:ring-ring/50"
    >
      <div className="flex items-center gap-3">
        {/* Decorative: the name follows, so screen readers announce "Chess Club…", not "CC Chess Club…". */}
        <span aria-hidden="true"><ClubAvatar name={club.name ?? ""} url={club.avatarUrl} size="lg" /></span>
        <div className="min-w-0 flex-1">
          <h3 className="truncate font-medium group-hover:text-primary">{club.name}</h3>
          <p className="text-xs text-muted-foreground">{club.category}</p>
        </div>
        {club.status === "ARCHIVED" && <StatusBadge status="ARCHIVED" />}
      </div>
      <p className="line-clamp-2 text-sm text-muted-foreground">{club.tagline || "No tagline yet."}</p>
      <p className="mt-auto flex items-center gap-1.5 text-xs text-muted-foreground">
        <UsersIcon className="size-3.5" aria-hidden="true" />
        {members} {members === 1 ? "member" : "members"}
      </p>
    </Link>
  );
}
