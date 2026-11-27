import { CircleCheckIcon, CircleDashedIcon, CircleDotIcon } from "lucide-react";
import type { LinkedWorkItem } from "./api";

/** "In progress · planned for v1.2.0", "Released in v1.2.0"… */
export function progress(w: LinkedWorkItem): string {
  if (w.state === "DONE") {
    if (w.released && w.milestone) return `Released in ${w.milestone}`;
    return w.milestone ? `Done · coming in ${w.milestone}` : "Done";
  }
  const state = w.state === "IN_PROGRESS" ? "In progress" : "Planned";
  return w.milestone ? `${state} · planned for ${w.milestone}` : state;
}

const ICON = { PLANNED: CircleDashedIcon, IN_PROGRESS: CircleDotIcon, DONE: CircleCheckIcon } as const;

/** The development work a ticket is linked to (E18-S03): what it is, and how far it is. */
export function LinkedWork({ items }: { items: LinkedWorkItem[] }) {
  return (
    <section aria-labelledby="linked-work-heading" className="space-y-2">
      <h2 id="linked-work-heading" className="text-lg font-semibold">Linked work</h2>
      <p className="text-sm text-muted-foreground">The team is answering this ticket with this work:</p>
      <ul className="space-y-2">
        {items.map((w) => {
          const Icon = ICON[w.state ?? "PLANNED"];
          return (
            <li key={w.ref} className="flex items-start gap-2 rounded-lg border p-3 text-sm">
              <Icon className={w.state === "DONE" ? "mt-0.5 size-4 text-primary" : "mt-0.5 size-4 text-muted-foreground"} aria-hidden="true" />
              <span>
                <span className="block font-medium">{w.ref} · {w.title}</span>
                <span className="block text-xs text-muted-foreground">{progress(w)}</span>
              </span>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
