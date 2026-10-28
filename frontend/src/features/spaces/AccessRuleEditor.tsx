import { GroupPicker, useClubGroups } from "@/features/groups";
import type { AccessLevel, Rule } from "./api";

const LEVELS: { level: AccessLevel; label: string }[] = [
  { level: "PUBLIC", label: "Everyone" },
  { level: "CLUB_MEMBERS", label: "Club members" },
  { level: "GROUPS", label: "Some groups" },
];

/** R-23, as the backend applies it: posting can't be wider than reading. */
export function effectiveWrite(read: Rule, write: Rule): Rule {
  return write.level === "PUBLIC" && read.level !== "PUBLIC" ? read : write;
}

function who(rule: Rule, groupNames: Map<string, string>): string {
  if (rule.level === "PUBLIC") return "Everyone";
  if (rule.level === "CLUB_MEMBERS") return "Club members";
  const names = (rule.groupIds ?? []).map((id) => groupNames.get(id) ?? "a deleted group");
  return names.length ? names.join(", ") : "Nobody";
}

/** "Everyone can read · Board can post": what a space's rules mean, in one sentence. */
export function audienceSentence(read: Rule, write: Rule, groupNames: Map<string, string>): string {
  return `${who(read, groupNames)} can read · ${who(effectiveWrite(read, write), groupNames)} can post`;
}

/**
 * Who can read and who can post a space (E05-S03): everyone, the club's members, or some of its groups (BR-12).
 * The sentence below says what the two rules mean together.
 */
export function AccessRuleEditor({ clubId, read, write, onChange }: {
  clubId: string;
  read: Rule;
  write: Rule;
  onChange: (read: Rule, write: Rule) => void;
}) {
  const { data: groups = [] } = useClubGroups(clubId);
  const names = new Map(groups.map((g) => [g.id ?? "", g.name ?? ""]));
  const narrowed = effectiveWrite(read, write) !== write;

  return (
    <div className="space-y-4">
      <RuleField legend="Who can read" name="read" clubId={clubId} rule={read} onChange={(r) => onChange(r, write)} />
      <RuleField legend="Who can post" name="write" clubId={clubId} rule={write} onChange={(w) => onChange(read, w)} />
      <p aria-live="polite" className="rounded-lg bg-muted px-3 py-2 text-sm">
        {audienceSentence(read, write, names)}
        {narrowed && <span className="block text-xs text-muted-foreground">Posting can't be wider than reading.</span>}
      </p>
    </div>
  );
}

function RuleField({ legend, name, clubId, rule, onChange }: {
  legend: string;
  name: string;
  clubId: string;
  rule: Rule;
  onChange: (rule: Rule) => void;
}) {
  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-medium">{legend}</legend>
      <div className="flex flex-wrap gap-4">
        {LEVELS.map(({ level, label }) => (
          <label key={level} className="flex items-center gap-2 text-sm">
            <input type="radio" name={name} className="accent-primary" checked={rule.level === level}
                   onChange={() => onChange({ level, groupIds: level === "GROUPS" ? rule.groupIds ?? [] : [] })} />
            {label}
          </label>
        ))}
      </div>
      {rule.level === "GROUPS" && (
        <div className="ml-6">
          <GroupPicker clubId={clubId} label={`${legend}: groups`} value={rule.groupIds ?? []}
                       onChange={(groupIds) => onChange({ level: "GROUPS", groupIds })} />
        </div>
      )}
    </fieldset>
  );
}
