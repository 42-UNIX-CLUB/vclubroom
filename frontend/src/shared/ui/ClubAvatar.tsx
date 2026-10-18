import { Avatar, AvatarFallback, AvatarImage } from "./avatar";

/** Club (or user) picture with initials as the fallback while loading or when there is none. */
export function ClubAvatar({ name, url, size = "default" }: {
  name: string;
  url?: string | null;
  size?: "sm" | "default" | "lg";
}) {
  const initials = name.split(/\s+/).filter(Boolean).slice(0, 2).map((word) => word[0]!.toUpperCase()).join("");
  return (
    <Avatar size={size}>
      {url && <AvatarImage src={url} alt="" />}
      <AvatarFallback>{initials || "?"}</AvatarFallback>
    </Avatar>
  );
}