import { SearchIcon, SearchXIcon, XIcon } from "lucide-react";
import { type ReactNode, useEffect, useState } from "react";
import { useSearchParams } from "react-router";
import { cn } from "@/shared/lib/utils";
import { useDebounce } from "@/shared/lib/useDebounce";
import { Button } from "@/shared/ui/button";
import { EmptyState } from "@/shared/ui/EmptyState";
import { Input } from "@/shared/ui/input";
import { PageHeader } from "@/shared/ui/PageHeader";
import { Skeleton } from "@/shared/ui/skeleton";
import { Switch } from "@/shared/ui/switch";
import { Tabs, TabsList, TabsTrigger } from "@/shared/ui/tabs";
import { type DirectoryFilters, PAGE_SIZE, useCategories, useDirectory } from "./api";
import { ClubCard } from "./ClubCard";

const SORTS = [
  { value: "active", label: "Most active" },
  { value: "new", label: "Newest" },
  { value: "members", label: "Most members" },
  { value: "name", label: "A–Z" },
];

function readFilters(params: URLSearchParams): DirectoryFilters {
  return {
    q: params.get("q") ?? undefined,
    category: params.get("category") ?? undefined,
    tag: params.get("tag") ?? undefined,
    sort: params.get("sort") ?? "active",
    status: params.get("status") === "ALL" ? "ALL" : "ACTIVE",
    page: Math.max(Number(params.get("page") ?? 0) || 0, 0),
  };
}

/** /clubs, public: search, filters, sort and pages, all kept in the URL query. */
export function ClubDirectoryPage() {
  const [params, setParams] = useSearchParams();
  const filters = readFilters(params);
  const [search, setSearch] = useState(filters.q ?? "");
  const debouncedSearch = useDebounce(search.trim());
  const { data: categories } = useCategories();
  const { data: result, isPending } = useDirectory(filters);

  /** Changes some query parameters; undefined removes one. Any filter change goes back to the first page. */
  function update(changes: Record<string, string | undefined>) {
    setParams((current) => {
      const next = new URLSearchParams(current);
      Object.entries({ page: undefined, ...changes }).forEach(([key, value]) => {
        if (value) next.set(key, value);
        else next.delete(key);
      });
      return next;
    });
  }

  // The search box writes to the URL only once the user pauses typing.
  useEffect(() => {
    setParams((current) => {
      if ((current.get("q") ?? "") === debouncedSearch) return current;
      const next = new URLSearchParams(current);
      if (debouncedSearch) next.set("q", debouncedSearch);
      else next.delete("q");
      next.delete("page");
      return next;
    }, { replace: true });
  }, [debouncedSearch, setParams]);

  function goToPage(page: number) {
    update({ page: page > 0 ? String(page) : undefined });
    window.scrollTo({ top: 0 });
  }

  function clearFilters() {
    setSearch("");
    setParams(new URLSearchParams());
  }

  const total = result?.totalElements ?? 0;
  const pages = Math.max(Math.ceil(total / PAGE_SIZE), 1);

  return (
    <div className="space-y-6">
      <PageHeader title="Clubs" description="Find a club to join, or look back at clubs that are archived." />

      <div className="space-y-4">
        <div className="flex flex-wrap items-center gap-x-6 gap-y-3">
          <div className="relative min-w-60 flex-1">
            <SearchIcon className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
            <Input
              type="search"
              aria-label="Search clubs"
              placeholder="Search by name, topic or tag"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              className="pl-8"
            />
          </div>
          <label className="flex items-center gap-2 text-sm">
            <Switch
              checked={filters.status === "ALL"}
              onCheckedChange={(on) => update({ status: on ? "ALL" : undefined })}
            />
            Show archived clubs
          </label>
        </div>

        <div role="group" aria-label="Category" className="flex flex-wrap gap-2">
          <Chip pressed={!filters.category} onClick={() => update({ category: undefined })}>All</Chip>
          {categories?.map((category) => (
            <Chip key={category} pressed={filters.category === category} onClick={() => update({ category })}>
              {category}
            </Chip>
          ))}
          {filters.tag && (
            <Chip pressed onClick={() => update({ tag: undefined })} label={`Remove the tag filter ${filters.tag}`}>
              #{filters.tag} <XIcon className="size-3.5" aria-hidden="true" />
            </Chip>
          )}
        </div>

        <Tabs value={filters.sort} onValueChange={(sort) => update({ sort: sort === "active" ? undefined : String(sort) })}>
          <TabsList aria-label="Sort">
            {SORTS.map(({ value, label }) => <TabsTrigger key={value} value={value}>{label}</TabsTrigger>)}
          </TabsList>
        </Tabs>
      </div>

      {isPending ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-hidden="true">
          {Array.from({ length: 6 }, (_, i) => <Skeleton key={i} className="h-36" />)}
        </div>
      ) : total === 0 ? (
        <EmptyState
          icon={SearchXIcon}
          title="No clubs match"
          description="Try other words, another category, or include archived clubs."
          action={<Button variant="outline" onClick={clearFilters}>Clear filters</Button>}
        />
      ) : (
        <section aria-label="Results" className="space-y-4">
          <p className="text-sm text-muted-foreground">{total} {total === 1 ? "club" : "clubs"}</p>
          <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {result?.items?.map((club) => <li key={club.clubId}><ClubCard club={club} /></li>)}
          </ul>
          {pages > 1 && (
            <nav aria-label="Pages" className="flex items-center justify-center gap-3">
              <Button variant="outline" disabled={filters.page === 0} onClick={() => goToPage(filters.page - 1)}>
                Previous
              </Button>
              <span className="text-sm text-muted-foreground">Page {filters.page + 1} of {pages}</span>
              <Button variant="outline" disabled={filters.page + 1 >= pages} onClick={() => goToPage(filters.page + 1)}>
                Next
              </Button>
            </nav>
          )}
        </section>
      )}
    </div>
  );
}

function Chip({ pressed, onClick, label, children }: { pressed: boolean; onClick: () => void; label?: string; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={pressed}
      aria-label={label}
      onClick={onClick}
      className={cn(
        "inline-flex items-center gap-1 rounded-full border px-3 py-1 text-sm transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
        pressed ? "border-primary bg-primary text-primary-foreground" : "hover:border-primary/50 hover:bg-accent/40",
      )}
    >
      {children}
    </button>
  );
}
