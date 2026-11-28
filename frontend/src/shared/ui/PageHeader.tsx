import type { ReactNode } from "react";
import { usePageTitle } from "@/shared/lib/usePageTitle";

/**
 * Title row at the top of every page; `actions` sits on the right (buttons, filters). Pages inside a club pass
 * level 2: the club's name is their h1 (ClubLayout).
 */
export function PageHeader({ title, description, actions, level = 1 }: {
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
  level?: 1 | 2;
}) {
  const Heading = level === 2 ? "h2" : "h1";
  usePageTitle(title);
  return (
    <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
      <div className="space-y-1">
        <Heading className="text-2xl font-semibold tracking-tight">{title}</Heading>
        {description && <p className="text-sm text-muted-foreground">{description}</p>}
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  );
}