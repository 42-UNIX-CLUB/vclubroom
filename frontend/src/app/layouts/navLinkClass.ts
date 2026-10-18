import { cn } from "@/shared/lib/utils";

/** Shared look of navigation links in the top bar, club sidebar and admin nav. */
export function navLinkClass({ isActive }: { isActive: boolean }) {
  return cn(
    "rounded-md px-3 py-1.5 text-sm font-medium transition-colors hover:bg-muted",
    isActive ? "bg-muted text-foreground" : "text-muted-foreground",
  );
}