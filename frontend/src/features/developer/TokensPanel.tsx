import { CopyIcon, KeyRoundIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { formatDate, formatDateTime } from "@/shared/lib/dates";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { ConfirmDialog } from "@/shared/ui/ConfirmDialog";
import { Input } from "@/shared/ui/input";
import { type CreatedToken, type Token, useRevokeToken, useTokens } from "./api";
import { CreateTokenDialog } from "./CreateTokenDialog";
import { MAX_ACTIVE_TOKENS, describeScopes } from "./scopes";

/**
 * Settings › Developer tokens (E20-S02): personal access tokens for your own scripts and apps. A new token's value is
 * shown once, right after it is made; the list shows what each may do, when it expires and when it was last used.
 */
export function TokensPanel() {
  const { data: tokens } = useTokens();
  const revoke = useRevokeToken();
  const [creating, setCreating] = useState(false);
  const [created, setCreated] = useState<CreatedToken | null>(null);
  const [revoking, setRevoking] = useState<Token | null>(null);
  const full = (tokens ?? []).filter((t) => !t.expired).length >= MAX_ACTIVE_TOKENS;

  return (
    <div className="space-y-3 text-sm">
      <p className="text-muted-foreground">
        Let your own scripts and apps use the Virtual Clubroom API as you, within the scopes you choose. Send the token in
        an <code>Authorization: Bearer</code> header; the <a className="underline" href="/swagger-ui.html">API docs</a> name
        the scope each endpoint needs.
      </p>
      {created && (
        <div className="space-y-2 rounded-lg border p-3">
          <p className="font-medium">“{created.details?.name}” is ready. Copy it now: it won't be shown again.</p>
          <div className="flex gap-2">
            <Input aria-label="New token" readOnly value={created.token ?? ""} className="font-mono text-xs"
                   onFocus={(e) => e.target.select()} />
            <Button variant="outline"
                    onClick={() => void navigator.clipboard?.writeText(created.token ?? "").then(() => toast.success("Copied."))}>
              <CopyIcon aria-hidden="true" /> Copy
            </Button>
          </div>
          <p className="text-muted-foreground">Anyone with it can act as you within its scopes: keep it out of code you share.</p>
        </div>
      )}
      {tokens?.length === 0 && <p>You have no tokens.</p>}
      <ul aria-label="Your tokens" className="space-y-2">
        {tokens?.map((token) => (
          <li key={token.id} className="flex flex-wrap items-start justify-between gap-2 rounded-lg border p-3">
            <div className="min-w-0 space-y-1">
              <p className="flex flex-wrap items-center gap-2 font-medium">
                {token.name}
                <span className="font-mono text-xs font-normal text-muted-foreground">vcr_pat_…{token.lastFour}</span>
                {token.expired ? <Badge variant="destructive">Expired</Badge>
                  : !token.expiresAt && <Badge variant="outline">Never expires</Badge>}
              </p>
              <p className="text-xs text-muted-foreground">{describeScopes(token.scopes ?? [])}</p>
              <p className="text-xs text-muted-foreground">
                Created {token.createdAt && formatDate(token.createdAt)}
                {token.expiresAt && ` · ${token.expired ? "Expired" : "Expires"} ${formatDate(token.expiresAt)}`}
                {token.lastUsedAt ? ` · Last used ${formatDateTime(token.lastUsedAt)}` : " · Never used"}
              </p>
            </div>
            <Button variant="ghost" size="sm" onClick={() => setRevoking(token)}>Revoke</Button>
          </li>
        ))}
      </ul>
      <Button disabled={full} onClick={() => setCreating(true)}><KeyRoundIcon aria-hidden="true" /> New token</Button>
      {full && <p className="text-muted-foreground">You have {MAX_ACTIVE_TOKENS} active tokens: revoke one to create another.</p>}
      {creating && (
        <CreateTokenDialog onClose={() => setCreating(false)}
                           onCreated={(token) => { setCreated(token); setCreating(false); }} />
      )}
      <ConfirmDialog open={revoking !== null} title={`Revoke “${revoking?.name ?? ""}”?`} confirmLabel="Revoke"
                     description="The scripts and apps using it stop working at once. This can't be undone."
                     onConfirm={async () => {
                       await revoke.mutateAsync(revoking?.id ?? "");
                       if (created?.details?.id === revoking?.id) setCreated(null);
                     }}
                     onClose={() => setRevoking(null)} />
    </div>
  );
}
