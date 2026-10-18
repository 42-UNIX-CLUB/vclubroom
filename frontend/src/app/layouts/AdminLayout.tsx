import { NavLink, Outlet } from "react-router";
import { navLinkClass } from "./navLinkClass";

const PAGES = [
  { path: "approvals", label: "Approvals" },
  { path: "revivals", label: "Revivals" },
  { path: "clubs", label: "Clubs" },
  { path: "users", label: "Users" },
  { path: "campus-groups", label: "Campus groups" },
];

/** Placeholder admin console frame. E15-S01 turns it into the side nav with pending-count badges. */
export function AdminLayout() {
  return (
    <div className="space-y-6">
      <nav className="flex flex-wrap gap-1 border-b pb-3" aria-label="Admin">
        {PAGES.map(({ path, label }) => (
          <NavLink key={path} to={`/admin/${path}`} className={navLinkClass}>{label}</NavLink>
        ))}
      </nav>
      <Outlet />
    </div>
  );
}