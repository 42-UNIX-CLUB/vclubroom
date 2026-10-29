import { Link, NavLink, Outlet, useLocation } from "react-router";
import { PostLoginRedirect, UserMenu, useMe } from "@/features/auth";
import { OwnershipInvitations } from "@/features/membership";
import { NotificationBell } from "@/features/notifications";
import { RealtimeProvider } from "@/shared/realtime/RealtimeProvider";
import { buttonVariants } from "@/shared/ui/button";
import { ThemeToggle } from "@/shared/ui/ThemeToggle";
import { navLinkClass } from "./navLinkClass";

/** Top bar + page area for every route; logged-in users also get the live connection. */
export function AppLayout() {
  const { data: me } = useMe();
  const location = useLocation();
  const loginLink = location.pathname === "/login"
    ? "/login"
    : `/login?returnTo=${encodeURIComponent(location.pathname + location.search)}`;

  return (
    <RealtimeProvider enabled={!!me}>
      <div className="min-h-svh bg-background">
        <PostLoginRedirect />
        <header className="sticky top-0 z-40 border-b bg-background/90 backdrop-blur">
          <div className="mx-auto flex h-14 max-w-6xl items-center gap-1 px-4 sm:gap-2">
            <Link to="/" className="mr-2 flex shrink-0 items-center gap-2 font-semibold sm:mr-4">
              <img src="/duck-no-border.svg" alt="" className="size-6" />
              <span className="sr-only sm:not-sr-only">Virtual Clubroom</span>
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
                  <NotificationBell />
                  <UserMenu me={me} />
                </>
              ) : (
                <Link to={loginLink} className={buttonVariants({ size: "sm" })}>Log in</Link>
              )}
            </div>
          </div>
        </header>
        <main className="mx-auto max-w-6xl px-4 py-8">
          {me && <OwnershipInvitations />}
          <Outlet />
        </main>
      </div>
    </RealtimeProvider>
  );
}
