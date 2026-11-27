/** The API's areas, as the backend has them (shared.security.ApiArea): one read and one write scope each. */
export const AREAS = [
  { id: "profile", label: "Profile", description: "Your profile, and the people directory" },
  { id: "clubs", label: "Clubs", description: "Clubs: their spaces, channels, members, roles and invitations" },
  { id: "forum", label: "Forum", description: "Posts, comments and reactions" },
  { id: "chat", label: "Chat", description: "Chat messages" },
  { id: "events", label: "Events", description: "Events, RSVPs and calendars" },
  { id: "notifications", label: "Notifications", description: "Notifications and their preferences" },
  { id: "support", label: "Support", description: "Support tickets" },
  { id: "media", label: "Uploads", description: "Uploads" },
] as const;

/** Active (not expired) tokens per person (backend AccessTokenService.MAX_ACTIVE). */
export const MAX_ACTIVE_TOKENS = 5;

export const EXPIRY_CHOICES = [
  { id: "7", label: "7 days" },
  { id: "30", label: "30 days" },
  { id: "60", label: "60 days" },
  { id: "90", label: "90 days" },
  { id: "365", label: "1 year" },
  { id: "date", label: "A date" },
  { id: "never", label: "Never" },
] as const;

export type ExpiryChoice = (typeof EXPIRY_CHOICES)[number]["id"];

/** When a token made now expires: a number of days, the end of a chosen day (local time), or never (null). */
export function expiresAt(choice: ExpiryChoice, date: string, now = new Date()): string | null {
  if (choice === "never") return null;
  if (choice === "date") return new Date(`${date}T23:59:59`).toISOString();
  return new Date(now.getTime() + Number(choice) * 24 * 60 * 60 * 1000).toISOString();
}

/** "Forum: read, write · Profile: read", in the areas' order. */
export function describeScopes(scopes: readonly string[]): string {
  return AREAS.flatMap((area) => {
    const kinds = ["read", "write"].filter((kind) => scopes.includes(`${area.id}:${kind}`));
    return kinds.length ? [`${area.label}: ${kinds.join(", ")}`] : [];
  }).join(" · ");
}
