import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

/** Friendly placeholder for empty lists, missing pages and "coming soon" screens. */
export function EmptyState({ icon: Icon, title, description, action }: {
  icon?: LucideIcon;
  title: string;
  description?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed p-10 text-center">
      {Icon && <Icon className="size-8 text-muted-foreground" aria-hidden="true" />}
      <h2 className="text-lg font-medium">{title}</h2>
      {description && <p className="max-w-md text-sm text-muted-foreground">{description}</p>}
      {action}
    </div>
  );
}