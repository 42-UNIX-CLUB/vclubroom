import { BellIcon } from "lucide-react";
import { Link, NavLink, Outlet, useLocation } from "react-router";
import { PostLoginRedirect, UserMenu, useMe } from "@/features/auth";
import { buttonVariants } from "@/shared/ui/button";
import { ThemeToggle } from "@/shared/ui/ThemeToggle";
import { navLinkClass } from "./navLinkClass";

/** Top bar + page area for every route. */
export function AppLayout() {
  const { data: me } = useMe();
  const location = useLocation();
  const loginLink = location.pathname === "/login"
    ? "/login"
    : `/login?returnTo=${encodeURIComponent(location.pathname + location.search)}`;

  return (
    <div className="min-h-svh bg-background">
      <PostLoginRedirect />
      <header className="sticky top-0 z-40 border-b bg-background/90 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-2 px-4">
          <Link to="/" className="mr-4 flex items-center gap-2 font-semibold">
            <span className="size-6 rounded-md bg-primary" aria-hidden="true" />
            Virtual Clubroom
          </Link>
          <nav className="flex items-center gap-1" aria-label="Main">
            <NavLink to="/clubs" className={navLinkClass}>Clubs</NavLink>
            {me && <NavLink to="/calendar" className={navLinkClass}>Calendar</NavLink>}
            {me?.admin && <NavLink to="/admin" className={navLinkClass}>Admin</NavLink>}
          </nav>
          <div className="ml-auto flex items-center gap-1">
            <ThemeToggle />
            {me ? (
              <>
                {/* E12-S01 replaces this link with the NotificationBell (live unread count, dropdown). */}
                <Link to="/notifications" aria-label="Notifications" className={buttonVariants({ variant: "ghost", size: "icon" })}>
                  <BellIcon />
                </Link>
                <UserMenu me={me} />
              </>
            ) : (
              <Link to={loginLink} className={buttonVariants({ size: "sm" })}>Log in</Link>
            )}
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  );
}