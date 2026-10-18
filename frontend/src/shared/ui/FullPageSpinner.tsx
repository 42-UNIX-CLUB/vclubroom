import { Loader2Icon } from "lucide-react";

/** Shown while a page or the current user is loading. */
export function FullPageSpinner() {
  return (
    <div className="flex min-h-[50vh] items-center justify-center" role="status">
      <Loader2Icon className="size-6 animate-spin text-muted-foreground" aria-hidden="true" />
      <span className="sr-only">Loading…</span>
    </div>
  );
}