import { useState } from "react";
import { Input } from "@/shared/ui/input";
import { type CampusGroup, type ClubGroup, useCampusGroups, useClubGroups } from "./api";

/**
 * Picks groups for an access rule (E05-S03's AccessRuleEditor): the club's own, then campus groups (E15-S03), which
 * can be many (every pool and cursus synced from 42): a filter narrows them, and those already picked always show.
 * value and onChange are group ids.
 */
export function GroupPicker({ clubId, value, onChange, label = "Groups" }: {
  clubId: string;
  value: string[];
  onChange: (groupIds: string[]) => void;
  label?: string;
}) {
  const { data: clubGroups } = useClubGroups(clubId);
  const { data: campusGroups = [] } = useCampusGroups();
  const [filter, setFilter] = useState("");
  if (!clubGroups) return null;
  const query = filter.trim().toLowerCase();
  const campusShown = campusGroups.filter((g) => value.includes(g.id ?? "") || !query || (g.name ?? "").toLowerCase().includes(query));

  function toggle(groupId: string, on: boolean) {
    onChange(on ? [...value, groupId] : value.filter((id) => id !== groupId));
  }

  return (
    <fieldset className="space-y-3">
      <legend className="sr-only">{label}</legend>
      <div className="space-y-1">
        <p className="text-xs font-medium text-muted-foreground">Club groups</p>
        {clubGroups.length === 0
          ? <p className="text-sm text-muted-foreground">The club has no groups yet: create them in Settings › Groups.</p>
          : clubGroups.map((group) => <GroupBox key={group.id} group={group} checked={value.includes(group.id ?? "")} onToggle={toggle} />)}
      </div>
      {campusGroups.length > 0 && (
        <div className="space-y-1">
          <p className="text-xs font-medium text-muted-foreground">Campus groups</p>
          {campusGroups.length > 8 && (
            <Input aria-label={`${label}: find a campus group`} placeholder="Find a campus group" className="h-8 max-w-64"
                   value={filter} onChange={(event) => setFilter(event.target.value)} />
          )}
          {campusShown.map((group) => <GroupBox key={group.id} group={group} checked={value.includes(group.id ?? "")} onToggle={toggle} />)}
        </div>
      )}
    </fieldset>
  );
}

function GroupBox({ group, checked, onToggle }: { group: ClubGroup | CampusGroup; checked: boolean; onToggle: (id: string, on: boolean) => void }) {
  return (
    <label className="flex items-center gap-2 text-sm">
      <input type="checkbox" className="accent-primary" checked={checked} onChange={(event) => onToggle(group.id ?? "", event.target.checked)} />
      {group.name}
      <span className="text-muted-foreground">({group.memberCount ?? 0})</span>
    </label>
  );
}
