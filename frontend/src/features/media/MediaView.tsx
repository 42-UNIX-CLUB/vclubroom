import { cn } from "@/shared/lib/utils";

/** What the backend's MediaView carries (posts, chat messages, avatars…). */
export interface MediaItem {
  kind?: "IMAGE" | "VIDEO" | "AUDIO";
  url?: string;
  contentType?: string;
}

/** The right player for the kind: video and audio stream from storage with range requests (seeking works). */
export function MediaView({ media, alt = "", className }: { media: MediaItem; alt?: string; className?: string }) {
  if (media.kind === "VIDEO") {
    return <video controls preload="metadata" src={media.url} className={cn("max-h-[28rem] w-full rounded-lg bg-black", className)} />;
  }
  if (media.kind === "AUDIO") {
    return <audio controls preload="metadata" src={media.url} className={cn("w-full", className)} />;
  }
  return <img src={media.url} alt={alt} loading="lazy" className={cn("max-h-[28rem] rounded-lg object-contain", className)} />;
}
