import { formatDate } from "@/shared/lib/dates";
import { cn } from "@/shared/lib/utils";

export interface Era {
  number?: number;
  startedAt?: string;
  endedAt?: string;
}

/** One button per era, newest first, with its dates; the chosen one is pressed. */
export function EraSwitcher({ eras, selected, onSelect }: { eras: Era[]; selected: number; onSelect: (era: number) => void }) {
  return (
    <div role="group" aria-label="Eras" className="flex flex-wrap gap-2">
      {[...eras].reverse().map((era) => (
        <button
          key={era.number}
          type="button"
          aria-pressed={era.number === selected}
          onClick={() => onSelect(era.number ?? 0)}
          className={cn(
            "rounded-lg border px-3 py-1.5 text-left text-sm transition-colors outline-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50",
            era.number === selected && "border-primary/50 bg-primary/10",
          )}
        >
          <span className="block font-medium">Era {era.number}</span>
          <span className="block text-xs text-muted-foreground">
            {era.startedAt && formatDate(era.startedAt)} – {era.endedAt ? formatDate(era.endedAt) : "now"}
          </span>
        </button>
      ))}
    </div>
  );
}
