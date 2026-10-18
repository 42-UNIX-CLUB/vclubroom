import { HammerIcon } from "lucide-react";
import { EmptyState } from "./EmptyState";
import { PageHeader } from "./PageHeader";

/** Stand-in for a route whose feature is not built yet; `story` names the backlog story that replaces it. */
export function PlaceholderPage({ title, story }: { title: string; story: string }) {
  return (
    <>
      <PageHeader title={title} />
      <EmptyState icon={HammerIcon} title="Coming soon" description={`This page arrives with ${story}.`} />
    </>
  );
}