import { Link } from "react-router";

/** At the bottom of every page (E19-S01, E19-S02): the API's documentation, and whether the platform works. */
export function SiteFooter() {
  return (
    <footer className="border-t">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-4 gap-y-1 px-4 py-6 text-sm text-muted-foreground">
        <span>Virtual Clubroom</span>
        <nav aria-label="Platform" className="flex flex-wrap gap-x-4">
          {/* Served by the backend (springdoc), not a page of the app: a full page load. */}
          <a href="/swagger-ui.html" className="hover:text-foreground">API docs</a>
          <Link to="/status" className="hover:text-foreground">Status</Link>
        </nav>
      </div>
    </footer>
  );
}
