import { toast } from "sonner";
import { useMe } from "@/features/auth";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { Skeleton } from "@/shared/ui/skeleton";
import { Switch } from "@/shared/ui/switch";
import { type Preference, type PreferenceChoice, usePreferences, useSavePreferences } from "./api";

type Type = NonNullable<Preference["type"]>;

/** What each type is about, in the words of the person receiving it, grouped as the form shows them. */
const GROUPS: { title: string; types: { type: Type; label: string; adminOnly?: boolean }[] }[] = [
  {
    title: "Clubs",
    types: [
      { type: "CLUB_APPROVED", label: "A club I asked for is approved" },
      { type: "CLUB_REJECTED", label: "A club I asked for is not approved" },
      { type: "CLUB_ARCHIVED", label: "A club I'm in is archived" },
      { type: "CLUB_REVIVED", label: "A club I was in is revived" },
      { type: "CLUB_CREATION_SUBMITTED", label: "Someone asks for a new club", adminOnly: true },
      { type: "CLUB_REVIVAL_REQUESTED", label: "Someone asks to revive a club", adminOnly: true },
    ],
  },
  {
    title: "Membership",
    types: [
      { type: "JOIN_REQUEST_RECEIVED", label: "Someone asks to join a club I approve requests for" },
      { type: "JOIN_REQUEST_DECIDED", label: "My request to join is answered" },
      { type: "OWNERSHIP_INVITATION_RECEIVED", label: "I'm invited to co-own a club" },
      { type: "OWNERSHIP_CHANGED", label: "I become, or stop being, an owner" },
    ],
  },
  {
    title: "Forum",
    types: [
      { type: "NEW_POST", label: "New posts in my clubs" },
      { type: "NEW_COMMENT", label: "Comments on my posts" },
      { type: "NEW_REPLY", label: "Replies to my comments" },
    ],
  },
  {
    title: "Events",
    types: [
      { type: "EVENT_SCHEDULED", label: "New events in my clubs" },
      { type: "EVENT_UPDATED", label: "An event I answered changes" },
      { type: "EVENT_CANCELLED", label: "An event I answered is cancelled" },
      { type: "EVENT_REMINDER", label: "Reminders before events I'm going to" },
    ],
  },
  {
    title: "Support",
    types: [
      { type: "SUPPORT_TICKET_RECEIVED", label: "A ticket I sent is received" },
      { type: "SUPPORT_TICKET_REPLIED", label: "The team answers one of my tickets" },
      { type: "SUPPORT_TICKET_UPDATED", label: "One of my tickets changes status" },
      { type: "SUPPORT_TICKET_CLOSED", label: "One of my tickets is closed" },
    ],
  },
];

function defaults(p: Preference): string {
  if (p.defaultInApp && p.defaultEmail) return "Default: in-app and email";
  if (p.defaultInApp) return "Default: in-app only";
  return p.defaultEmail ? "Default: email only" : "Default: off";
}

const isDefault = (p: Preference) => p.inApp === p.defaultInApp && p.email === p.defaultEmail;

/** Per type, in-app and email (R-15). Each switch saves at once; only what differs from the defaults is stored. */
export function PreferencesForm() {
  const { data: me } = useMe();
  const { data: preferences } = usePreferences();
  const save = useSavePreferences();

  if (!preferences) return <Skeleton className="h-64 w-full" />;
  const byType = new Map(preferences.map((p) => [p.type, p]));
  const changed = preferences.filter((p) => !isDefault(p));

  function choose(choices: PreferenceChoice[]) {
    save.mutate(choices, { onError: (error) => toast.error(error.message) });
  }

  return (
    <div className="space-y-6">
      {GROUPS.map((group) => {
        const rows = group.types.filter((t) => byType.has(t.type) && (!t.adminOnly || me?.admin));
        return (
          <table key={group.title} className="w-full text-sm">
            <caption className="pb-2 text-left text-base font-semibold">{group.title}</caption>
            <thead className="text-xs text-muted-foreground">
            <tr>
              <th scope="col" className="text-left font-normal"><span className="sr-only">Notification</span></th>
              <th scope="col" className="w-16 font-normal">In-app</th>
              <th scope="col" className="w-16 font-normal">Email</th>
            </tr>
            </thead>
            <tbody className="divide-y">
            {rows.map(({ type, label }) => {
              const p = byType.get(type)!;
              return (
                <tr key={type}>
                  <th scope="row" className="py-2 pr-2 text-left font-normal">
                    <span className="flex flex-wrap items-center gap-2">{label}{!isDefault(p) && <Badge variant="secondary">Changed</Badge>}</span>
                    <span className="block text-xs text-muted-foreground">{defaults(p)}</span>
                  </th>
                  <td className="text-center">
                    <Switch aria-label={`${label}: in-app`} checked={!!p.inApp}
                            onCheckedChange={(on) => choose([{ type, inApp: on, email: !!p.email }])} />
                  </td>
                  <td className="text-center">
                    <Switch aria-label={`${label}: email`} checked={!!p.email}
                            onCheckedChange={(on) => choose([{ type, inApp: !!p.inApp, email: on }])} />
                  </td>
                </tr>
              );
            })}
            </tbody>
          </table>
        );
      })}
      <Button variant="outline" size="sm" disabled={changed.length === 0 || save.isPending}
              onClick={() => choose(changed.map((p) => ({ type: p.type!, inApp: !!p.defaultInApp, email: !!p.defaultEmail })))}>
        Restore the defaults
      </Button>
    </div>
  );
}
