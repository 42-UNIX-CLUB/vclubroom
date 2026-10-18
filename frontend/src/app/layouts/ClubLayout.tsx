import { NavLink, Outlet, useParams } from "react-router";
import { navLinkClass } from "./navLinkClass";

const SECTIONS = [
  { path: "", label: "Home" },
  { path: "events", label: "Events" },
  { path: "members", label: "Members" },
  { path: "history", label: "History" },
  { path: "settings", label: "Settings" },
];

/**
 * Placeholder club frame. E04-S03 adds the header (banner, avatar, StatusBadge, JoinButton) and the right panel,
 * E05-S02 the readable spaces in the sidebar.
 */
export function ClubLayout() {
  const { slug } = useParams();
  return (
    <div className="grid gap-6 md:grid-cols-[14rem_1fr]">
      <aside aria-label="Club">
        <p className="px-3 pb-2 text-sm font-semibold">{slug}</p>
        <nav className="flex flex-col gap-1">
          {SECTIONS.map(({ path, label }) => (
            <NavLink key={label} to={path ? `/c/${slug}/${path}` : `/c/${slug}`} end={!path} className={navLinkClass}>
              {label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <section>
        <Outlet />
      </section>
    </div>
  );
}