import { NewspaperIcon } from "lucide-react";
import { Badge } from "@/shared/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card";
import { EmptyState } from "@/shared/ui/EmptyState";
import { Markdown } from "@/shared/ui/Markdown";
import { useCurrentClub } from "./currentClub";

/** /c/:slug: what the club is about, then its feed (E09-S05). */
export function ClubHome() {
  const club = useCurrentClub();
  const open = club.status === "ACTIVE" || club.status === "ARCHIVED";

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
      {open && <EmptyState icon={NewspaperIcon} title="No posts yet" description="The club feed arrives with E09-S05." />}
    </div>
  );
}
