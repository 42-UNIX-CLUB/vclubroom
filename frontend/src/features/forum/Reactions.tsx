import { SmilePlusIcon } from "lucide-react";
import { toast } from "sonner";
import { cn } from "@/shared/lib/utils";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/shared/ui/dropdown-menu";
import { type ReactionTarget, useReactionOptions, useToggleReaction } from "./api";

/**
 * The emoji under a post or a comment: those used so far with their counts (mine highlighted, a click toggles mine),
 * and a picker with the fixed set. Read-only targets only show the counts.
 */
export function Reactions({ target, counts, mine, readOnly }: {
  target: ReactionTarget;
  counts?: Record<string, number>;
  mine?: string[];
  readOnly?: boolean;
}) {
  const { data: options = [] } = useReactionOptions();
  const toggle = useToggleReaction(target);
  const myCodes = new Set(mine);
  const used = options.filter((option) => (counts?.[option.code ?? ""] ?? 0) > 0);

  function react(code: string) {
    toggle.mutate({ code, add: !myCodes.has(code) }, { onError: (error) => toast.error(error.message) });
  }

  if (readOnly && used.length === 0) return null;
  return (
    <div role="group" aria-label="Reactions" className="flex flex-wrap items-center gap-1">
      {used.map(({ code = "", emoji }) => (
        <button
          key={code}
          type="button"
          aria-pressed={myCodes.has(code)}
          disabled={readOnly}
          onClick={() => react(code)}
          className={cn(
            "inline-flex h-7 items-center gap-1 rounded-full border px-2 text-xs tabular-nums transition-colors outline-none enabled:hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50",
            myCodes.has(code) && "border-primary/50 bg-primary/10",
          )}
        >
          <span aria-hidden="true">{emoji}</span>
          <span className="sr-only">{code.replace("_", " ")}</span>
          {counts?.[code]}
        </button>
      ))}
      {!readOnly && (
        <DropdownMenu>
          <DropdownMenuTrigger
            aria-label="Add a reaction"
            className="inline-flex size-7 items-center justify-center rounded-full text-muted-foreground outline-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50"
          >
            <SmilePlusIcon className="size-4" />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start" className="flex w-auto min-w-0 gap-1 p-1">
            {options.map(({ code = "", emoji }) => (
              <DropdownMenuItem key={code} aria-label={code.replace("_", " ")} className="justify-center px-2 text-lg"
                onClick={() => react(code)}>
                {emoji}
              </DropdownMenuItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>
      )}
    </div>
  );
}
