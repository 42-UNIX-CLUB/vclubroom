import { Link } from "react-router";
import { ClubFeed } from "@/features/forum";
import { useClubSpaces } from "@/features/spaces";
import { Badge } from "@/shared/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card";
import { Markdown } from "@/shared/ui/Markdown";
import { useCurrentClub } from "./currentClub";

/** /c/:slug: what the club is about, then its feed: the latest posts of every channel you can read (E09-S05). */
export function ClubHome() {
  const club = useCurrentClub();
  const open = club.status === "ACTIVE" || club.status === "ARCHIVED";
  const { data: spaces } = useClubSpaces(open ? club.id ?? "" : "");
  const channels = new Map((spaces ?? []).flatMap((space) => (space.channels ?? []).map((channel) => [channel.id, { space, channel }])));

  function channelLabel(channelId: string) {
    const found = channels.get(channelId);
    return found && (
      <Link to={`/c/${club.slug}/s/${found.space.slug}/${found.channel.slug}`} className="hover:underline">
        in #{found.channel.name} · {found.space.name}
      </Link>
    );
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>About this club</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {club.description
            ? <Markdown>{club.description}</Markdown>
            : <p className="text-sm text-muted-foreground">No description yet.</p>}
          <div className="flex flex-wrap gap-2">
            <Badge variant="secondary">{club.category}</Badge>
            {club.tags?.map((tag) => <Badge key={tag} variant="outline">#{tag}</Badge>)}
          </div>
        </CardContent>
      </Card>
      {open && club.id && (
        <section aria-labelledby="feed-heading" className="space-y-3">
          <h2 id="feed-heading" className="text-lg font-semibold">Latest posts</h2>
          <ClubFeed clubId={club.id} era={null} channelLabel={channelLabel}
            empty={{ title: "No posts yet", description: "Posts from the club's channels show up here." }} />
        </section>
      )}
    </div>
  );
}
