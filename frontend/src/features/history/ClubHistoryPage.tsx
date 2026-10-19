import { HistoryIcon } from "lucide-react";
import { useEffect } from "react";
import { useSearchParams } from "react-router";
import { useCurrentClub } from "@/features/clubs";
import { ChannelFeed } from "@/features/forum";
import { useClubSpaces } from "@/features/spaces";
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card";
import { EmptyState } from "@/shared/ui/EmptyState";
import { EraSwitcher } from "./EraSwitcher";
import { Timeline } from "./Timeline";

/**
 * /c/:slug/history (08-frontend §9.3): the eras, the club's timeline, and the posts of the chosen era (?era=n), from
 * the club's first forum channel. Earlier eras are read-only (BR-15). E09-S05's club feed can replace the channel.
 */
export function ClubHistoryPage() {
  const club = useCurrentClub();
  const [params, setParams] = useSearchParams();
  const { data: spaces } = useClubSpaces(club.id ?? "");
  const currentEra = club.currentEra ?? 1;
  const era = Number(params.get("era")) || null;

  // The page always shows one era: the current one unless another is chosen (the feed's "Back" link clears ?era).
  useEffect(() => {
    if (era === null) setParams({ era: String(currentEra) }, { replace: true });
  }, [era, currentEra, setParams]);

  const forum = spaces?.flatMap((space) => space.channels ?? []).find((channel) => channel.type === "FORUM");
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
        {forum?.id
          ? era !== null && <ChannelFeed channel={{ id: forum.id, canWrite: false, currentEra }} />
          : spaces && <EmptyState icon={HistoryIcon} title="No posts you can read" />}
      </div>
    </section>
  );
}
