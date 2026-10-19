import { HammerIcon } from "lucide-react";
import { EmptyState } from "./EmptyState";
import { PageHeader } from "./PageHeader";

/**
 * Stand-in for a route whose feature is not built yet. Users read "being built"; `story` names the backlog story
 * that replaces it, shown only in development (npm run dev), never on the demo.
 */
export function PlaceholderPage({ title, story }: { title: string; story: string }) {
  return (
    <>
      <PageHeader title={title} />
      <EmptyState
        icon={HammerIcon}
        title="Coming soon"
        description="This part of Virtual Clubroom is still being built."
        action={import.meta.env.DEV ? <p className="text-xs text-muted-foreground">Backlog: {story}</p> : undefined}
      />
    </>
  );
}
