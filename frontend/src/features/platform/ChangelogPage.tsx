import { formatDate } from "@/shared/lib/dates";
import { Badge } from "@/shared/ui/badge";
import { EmptyState } from "@/shared/ui/EmptyState";
import { PageHeader } from "@/shared/ui/PageHeader";
import { Skeleton } from "@/shared/ui/skeleton";
import { type Release, useChangelog } from "./api";

/** "linked work on the ticket page" → "Linked work on the ticket page". */
const sentence = (text = "") => text.charAt(0).toUpperCase() + text.slice(1);

function ReleaseBlock({ release, open }: { release: Release; open: boolean }) {
  const title = release.version ?? "Coming in the next release";
  const count = release.sections?.reduce((n, s) => n + (s.changes?.length ?? 0), 0) ?? 0;
  return (
    <details open={open} className="group rounded-lg border p-4">
      <summary className="flex cursor-pointer flex-wrap items-baseline gap-x-2 font-semibold">
        <h2 className="text-lg">{title}</h2>
        <span className="text-sm font-normal text-muted-foreground">
          {release.date ? formatDate(release.date) : "merged, not released yet"} · {count} {count === 1 ? "change" : "changes"}
        </span>
      </summary>
      <div className="mt-3 space-y-4">
        {release.sections?.map((section) => (
          <section key={section.title} aria-label={`${title}: ${section.title}`}>
            <h3 className="mb-1 text-sm font-medium">{section.title}</h3>
            <ul className="space-y-1 text-sm">
              {section.changes?.map((change, i) => (
                <li key={i} className="flex flex-wrap items-center gap-2">
                  <span>{sentence(change.description)}</span>
                  {change.breaking && <Badge variant="destructive">Breaking</Badge>}
                  {change.scope && <span className="text-xs text-muted-foreground">{change.scope}</span>}
                </li>
              ))}
            </ul>
          </section>
        ))}
      </div>
    </details>
  );
}

/** /changelog (E19-S03): what changed in each release, newest first, from the commit history. */
export function ChangelogPage() {
  const { data, isPending, isError } = useChangelog();
  return (
    <>
      <PageHeader title="Changelog"
                  description="What changed in each release. Versions follow semantic versioning: a new feature raises the middle number, a fix the last one." />
      <div className="max-w-3xl space-y-3">
        {isPending && <Skeleton className="h-48" />}
        {isError && <p className="text-sm text-destructive">Could not load the changelog.</p>}
        {data && !data.releases?.length && <EmptyState title="No changelog yet" description="It appears with the next deploy." />}
        {data?.releases?.map((release, index) => <ReleaseBlock key={release.version ?? "next"} release={release} open={index < 2} />)}
      </div>
    </>
  );
}
