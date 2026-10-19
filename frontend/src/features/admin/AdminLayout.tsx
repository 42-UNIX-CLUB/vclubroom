import { NavLink, Outlet } from "react-router";
import { cn } from "@/shared/lib/utils";
import { Badge } from "@/shared/ui/badge";
import { usePendingClubCount, usePendingRevivalCount } from "./api";

const PAGES = [
  { path: "approvals", label: "Approvals" },
  { path: "revivals", label: "Revivals" },
  { path: "clubs", label: "Clubs" },
  { path: "users", label: "Users" },
  { path: "campus-groups", label: "Campus groups" },
];

/** Admin console frame (E15-S01): side navigation with the badges of what waits for a decision. */
export function AdminLayout() {
  const pendingApprovals = usePendingClubCount();
  const pendingRevivals = usePendingRevivalCount();
  return (
    <div className="grid gap-6 md:grid-cols-[12rem_1fr]">
      <nav aria-label="Admin" className="flex flex-col gap-1">
        <p className="px-3 pb-2 text-xs font-semibold tracking-wide text-muted-foreground uppercase">Admin console</p>
        {PAGES.map(({ path, label }) => (
          <NavLink
            key={path}
            to={`/admin/${path}`}
            className={({ isActive }) => cn(
              "flex items-center justify-between rounded-md px-3 py-1.5 text-sm font-medium transition-colors hover:bg-muted",
              isActive ? "bg-muted text-foreground" : "text-muted-foreground",
            )}
          >
            {label}
            {path === "approvals" && pendingApprovals > 0 && <Badge aria-label={`${pendingApprovals} pending`}>{pendingApprovals}</Badge>}
            {path === "revivals" && pendingRevivals > 0 && <Badge aria-label={`${pendingRevivals} pending revivals`}>{pendingRevivals}</Badge>}
          </NavLink>
        ))}
      </nav>
      <section>
        <Outlet />
      </section>
    </div>
  );
}
