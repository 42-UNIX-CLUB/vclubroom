import { useEffect } from "react";
import { useSearchParams } from "react-router";
import { useCurrentClub } from "@/features/clubs";
import { ClubFeed } from "@/features/forum";
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card";
import { EraSwitcher } from "./EraSwitcher";
import { Timeline } from "./Timeline";

/**
 * /c/:slug/history (08-frontend §9.3): the eras, the club's timeline, and the posts of the chosen era (?era=n) from
 * every channel you can read (the club feed, E09-S05). Earlier eras are read-only (BR-15).
 */
export function ClubHistoryPage() {
  const club = useCurrentClub();
  const [params, setParams] = useSearchParams();
  const currentEra = club.currentEra ?? 1;
  const era = Number(params.get("era")) || null;

  // The page always shows one era: the current one unless another is chosen.
  useEffect(() => {
    if (era === null) setParams({ era: String(currentEra) }, { replace: true });
  }, [era, currentEra, setParams]);

  return (
    <section className="space-y-6">
      <div className="space-y-3">
        <h2 className="text-lg font-semibold">History</h2>
        <EraSwitcher eras={club.eras ?? []} selected={era ?? currentEra}
          onSelect={(chosen) => setParams({ era: String(chosen) })} />
      </div>
      <Card size="sm">
        <CardHeader><CardTitle>Timeline</CardTitle></CardHeader>
        <CardContent><Timeline clubId={club.id ?? ""} /></CardContent>
      </Card>
      <div className="space-y-3">
        <h3 className="font-medium">Posts of era {era ?? currentEra}</h3>
        {era !== null && club.id && (
          <ClubFeed clubId={club.id} era={era}
            empty={{ title: `No posts in era ${era}`, description: "Or none in a channel you can read." }} />
        )}
      </div>
    </section>
  );
}
