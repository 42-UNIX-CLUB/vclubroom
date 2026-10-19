import { useState } from "react";
import { useDebounce } from "@/shared/lib/useDebounce";
import { Button } from "@/shared/ui/button";
import { ClubAvatar } from "@/shared/ui/ClubAvatar";
import { Input } from "@/shared/ui/input";
import { type UserSummary, useMemberSearch, useUserSearch } from "./api";

/**
 * Search people by login or name and pick one. With a clubId it searches that club's members who are not owners
 * (ownership invitations); without, everyone (groups, later).
 */
export function UserPicker({ value, onChange, label = "Search people", clubId }: {
  value: UserSummary | null;
  onChange: (user: UserSummary | null) => void;
  label?: string;
  clubId?: string;
}) {
  const [text, setText] = useState("");
  const query = useDebounce(text, 300);
  const everyone = useUserSearch(clubId ? "" : query);
  const members = useMemberSearch(clubId ?? "", clubId ? query : "");
  const { data: users = [], isFetching } = clubId ? members : everyone;

  if (value) {
    return (
      <div className="flex items-center gap-3 rounded-lg border p-2">
        <Person user={value} />
        <Button variant="ghost" size="sm" onClick={() => onChange(null)}>Change</Button>
      </div>
    );
  }
  return (
    <div className="space-y-2">
      <Input type="search" aria-label={label} placeholder="Login or name…" value={text} onChange={(event) => setText(event.target.value)} />
      {query.trim().length >= 2 && (users.length === 0
        ? <p className="text-sm text-muted-foreground">{isFetching ? "Searching…" : "Nobody found."}</p>
        : (
          <ul className="divide-y rounded-lg border" aria-label="People found">
            {users.map((user) => (
              <li key={user.id}>
                <button
                  type="button"
                  className="flex w-full items-center gap-3 p-2 text-left outline-none hover:bg-muted focus-visible:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50"
                  onClick={() => {
                    onChange(user);
                    setText("");
                  }}
                >
                  <Person user={user} />
                </button>
              </li>
            ))}
          </ul>
        ))}
    </div>
  );
}

function Person({ user }: { user: UserSummary }) {
  return (
    <span className="flex min-w-0 flex-1 items-center gap-3 text-sm">
      <ClubAvatar name={user.displayName ?? ""} url={user.avatarUrl} size="sm" />
      <span className="truncate"><span className="font-medium">{user.displayName}</span> <span className="text-muted-foreground">@{user.login}</span></span>
    </span>
  );
}
