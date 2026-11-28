import { type FormEvent, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/shared/ui/button";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/shared/ui/dialog";
import { Field, FieldLabel } from "@/shared/ui/field";
import { Input } from "@/shared/ui/input";
import { type CreatedToken, useCreateToken } from "./api";
import { AREAS, EXPIRY_CHOICES, type ExpiryChoice, expiresAt } from "./scopes";

/** A new personal access token (E20-S02): a name, the scopes it may use, and when it stops working. */
export function CreateTokenDialog({ onClose, onCreated }: { onClose: () => void; onCreated: (token: CreatedToken) => void }) {
  const create = useCreateToken();
  const [name, setName] = useState("");
  const [scopes, setScopes] = useState<Set<string>>(new Set());
  const [expiry, setExpiry] = useState<ExpiryChoice>("30");
  const [date, setDate] = useState("");
  const tomorrow = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
  const valid = name.trim() !== "" && scopes.size > 0 && (expiry !== "date" || date >= tomorrow);

  /** Writing an area implies reading it: ticking write ticks read, unticking read unticks write. */
  function toggle(area: string, kind: "read" | "write", on: boolean) {
    const next = new Set(scopes);
    if (on) {
      next.add(`${area}:${kind}`);
      if (kind === "write") next.add(`${area}:read`);
    } else {
      next.delete(`${area}:${kind}`);
      if (kind === "read") next.delete(`${area}:write`);
    }
    setScopes(next);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    try {
      onCreated(await create.mutateAsync({ name: name.trim(), scopes: [...scopes], expiresAt: expiresAt(expiry, date) }));
    } catch (error) {
      toast.error((error as Error).message);
    }
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>New token</DialogTitle>
          <DialogDescription>It acts as you, only within the scopes you tick, and never as an admin.</DialogDescription>
        </DialogHeader>
        <form onSubmit={(e) => void submit(e)} className="space-y-4">
          <Field>
            <FieldLabel htmlFor="token-name">Name</FieldLabel>
            <Input id="token-name" maxLength={80} value={name} placeholder="What uses it, e.g. My Discord bot"
                   onChange={(e) => setName(e.target.value)} />
          </Field>
          <fieldset className="space-y-2">
            <legend className="text-sm font-medium">Scopes</legend>
            <table className="w-full text-sm">
              <thead className="sr-only"><tr><th>Area</th><th>Read</th><th>Write</th></tr></thead>
              <tbody>
                {AREAS.map((area) => (
                  <tr key={area.id} className="border-t">
                    <td className="py-1.5 pr-2">
                      <span className="font-medium">{area.label}</span>
                      <span className="block text-xs text-muted-foreground">{area.description}</span>
                    </td>
                    {(["read", "write"] as const).map((kind) => (
                      <td key={kind} className="px-2 py-1.5">
                        <label className="flex items-center gap-1.5 whitespace-nowrap">
                          <input type="checkbox" aria-label={`${area.label}: ${kind}`} checked={scopes.has(`${area.id}:${kind}`)}
                                 onChange={(e) => toggle(area.id, kind, e.target.checked)} />
                          {kind === "read" ? "Read" : "Write"}
                        </label>
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </fieldset>
          <fieldset className="space-y-2">
            <legend className="text-sm font-medium">Expires</legend>
            <div className="flex flex-wrap gap-x-4 gap-y-1.5 text-sm">
              {EXPIRY_CHOICES.map((choice) => (
                <label key={choice.id} className="flex items-center gap-1.5">
                  <input type="radio" name="token-expiry" value={choice.id} checked={expiry === choice.id}
                         onChange={() => setExpiry(choice.id)} />
                  {choice.label}
                </label>
              ))}
            </div>
            {expiry === "date" && (
              <Input type="date" aria-label="Expiry date" min={tomorrow} value={date} className="w-auto"
                     onChange={(e) => setDate(e.target.value)} />
            )}
            {expiry === "never" && (
              <p role="note" className="text-sm text-amber-700 dark:text-amber-400">
                A token that never expires works until you revoke it, even if it leaks. Prefer an expiry, and make a new
                token when it ends.
              </p>
            )}
          </fieldset>
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
            <Button type="submit" disabled={!valid || create.isPending}>Create token</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
