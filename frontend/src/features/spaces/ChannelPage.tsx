import { MessageCircleIcon, NewspaperIcon, SearchXIcon } from "lucide-react";
import { useOutletContext, useParams } from "react-router";
import { Badge } from "@/shared/ui/badge";
import { EmptyState } from "@/shared/ui/EmptyState";
import { FullPageSpinner } from "@/shared/ui/FullPageSpinner";
import { useChannel, useClubSpaces } from "./api";
import { ChannelIcon } from "./ChannelIcon";

/**
 * /c/:slug/s/:spaceSlug/:channelSlug: finds the channel in the club's spaces, then shows the forum or the chat.
 * The club comes from the club frame's outlet context (importing the clubs feature would create an import cycle:
 * clubs imports spaces for the sidebar).
 */
export function ChannelPage() {
  const club = useOutletContext<{ id?: string }>();
  const { spaceSlug, channelSlug } = useParams();
  const { data: spaces } = useClubSpaces(club.id ?? "");
  const listed = spaces?.flatMap((space) => space.channels ?? [])
    .find((channel) => channel.spaceSlug === spaceSlug && channel.slug === channelSlug);
  const { data: fresh } = useChannel(listed?.id ?? "", !!listed?.id);

  if (!spaces) return <FullPageSpinner />;
  if (!listed) {
    return <EmptyState icon={SearchXIcon} title="Channel not found" description="It does not exist, or you cannot read it." />;
  }
  const channel = fresh ?? listed;
  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-center gap-2 border-b pb-3">
        <ChannelIcon type={channel.type} className="size-5 text-muted-foreground" />
        <h2 className="text-lg font-semibold">{channel.name}</h2>
        <span className="text-sm text-muted-foreground">in {channel.spaceName}</span>
        {!channel.canWrite && <Badge variant="secondary">Read-only</Badge>}
      </header>
      {channel.type === "CHAT"
        // E10-S02 replaces this with <ChatRoom channel={channel} /> from the chat feature.
        ? <EmptyState icon={MessageCircleIcon} title="Chat is coming" description="Live chat arrives with E10-S02." />
        // E09-S01 replaces this with <ChannelFeed channel={channel} /> from the forum feature.
        : <EmptyState icon={NewspaperIcon} title="Posts are coming" description="The forum arrives with E09-S01." />}
    </div>
  );
}
