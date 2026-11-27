import { Link } from "react-router";
import { useChangelog } from "./api";

/** At the bottom of every page (E19): the API's documentation, whether the platform works, what changed, the version. */
export function SiteFooter() {
  const { data } = useChangelog();
  return (
    <footer className="border-t">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-4 gap-y-1 px-4 py-6 text-sm text-muted-foreground">
        <span>Virtual Clubroom{data?.current && ` ${data.current}`}</span>
        <nav aria-label="Platform" className="flex flex-wrap gap-x-4">
          {/* Served by the backend (springdoc), not a page of the app: a full page load. */}
          <a href="/swagger-ui.html" className="hover:text-foreground">API docs</a>
          <Link to="/status" className="hover:text-foreground">Status</Link>
          <Link to="/changelog" className="hover:text-foreground">Changelog</Link>
        </nav>
      </div>
    </footer>
  );
}
