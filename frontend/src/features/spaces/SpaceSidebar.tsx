import { LockIcon } from "lucide-react";
import { NavLink } from "react-router";
import { cn } from "@/shared/lib/utils";
import { useClubSpaces } from "./api";
import { ChannelIcon } from "./ChannelIcon";

const channelClass = ({ isActive }: { isActive: boolean }) => cn(
  "flex items-center gap-2 rounded-md px-3 py-1.5 text-sm transition-colors hover:bg-muted",
  isActive ? "bg-muted font-medium text-foreground" : "text-muted-foreground",
);

/** The club's readable spaces and their channels (08-frontend §9.2); 🔒 marks spaces that are not public. */
export function SpaceSidebar({ clubId, clubSlug }: { clubId: string; clubSlug: string }) {
  const { data: spaces } = useClubSpaces(clubId);
  if (!spaces?.length) return null;
  return (
    <nav aria-label="Spaces" className="mt-4 space-y-4">
      {spaces.map((space) => (
        <div key={space.id}>
          <p className="flex items-center gap-1.5 px-3 pb-1 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
            {space.name}
            {space.readLevel !== "PUBLIC" && (
              <>
                <LockIcon className="size-3" aria-hidden="true" />
                <span className="sr-only">(restricted)</span>
              </>
            )}
          </p>
          <ul>
            {space.channels?.map((channel) => (
              <li key={channel.id}>
                <NavLink to={`/c/${clubSlug}/s/${space.slug}/${channel.slug}`} className={channelClass}>
                  <ChannelIcon type={channel.type} />
                  {channel.name}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </nav>
  );
}
