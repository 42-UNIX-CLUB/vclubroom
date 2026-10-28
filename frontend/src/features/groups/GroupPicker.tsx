import { useClubGroups } from "./api";

/**
 * Picks some of the club's groups, for an access rule (E05-S03's AccessRuleEditor). value and onChange are group ids.
 * Campus groups come with E15-S03.
 */
export function GroupPicker({ clubId, value, onChange, label = "Groups" }: {
  clubId: string;
  value: string[];
  onChange: (groupIds: string[]) => void;
  label?: string;
}) {
  const { data: groups } = useClubGroups(clubId);
  if (!groups) return null;
  if (groups.length === 0) {
    return <p className="text-sm text-muted-foreground">The club has no groups yet: create them in Settings › Groups.</p>;
  }
  return (
    <fieldset className="space-y-1">
      <legend className="sr-only">{label}</legend>
      {groups.map((group) => (
        <label key={group.id} className="flex items-center gap-2 text-sm">
          <input type="checkbox" className="accent-primary" checked={value.includes(group.id ?? "")}
                 onChange={(event) => onChange(event.target.checked
                   ? [...value, group.id ?? ""]
                   : value.filter((id) => id !== group.id))} />
          {group.name}
          <span className="text-muted-foreground">({group.memberCount ?? 0})</span>
        </label>
      ))}
    </fieldset>
  );
}
