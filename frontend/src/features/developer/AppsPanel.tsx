import { CopyIcon, PlusIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { ConfirmDialog } from "@/shared/ui/ConfirmDialog";
import { Input } from "@/shared/ui/input";
import { type App, type RegisteredApp, useApps, useDeleteApp, useNewAppSecret } from "./api";
import { AppDialog } from "./AppDialog";

/** Apps per person (backend OAuthAppService.MAX_APPS), like 42's intra. */
export const MAX_APPS = 3;

function copy(value: string) {
  void navigator.clipboard?.writeText(value).then(() => toast.success("Copied."));
}

/**
 * Settings › Your apps (E20-S04): OAuth apps you registered, so other students sign in to them with Virtual Clubroom.
 * A confidential app's secret is shown once, when registered or renewed.
 */
export function AppsPanel() {
  const { data: apps } = useApps();
  const newSecret = useNewAppSecret();
  const remove = useDeleteApp();
  const [editing, setEditing] = useState<App | "new" | null>(null);
  const [secret, setSecret] = useState<RegisteredApp | null>(null);
  const [renewing, setRenewing] = useState<App | null>(null);
  const [deleting, setDeleting] = useState<App | null>(null);
  const full = (apps?.length ?? 0) >= MAX_APPS;

  return (
    <div className="space-y-3 text-sm">
      <p className="text-muted-foreground">
        Build an app other students sign in to with their Virtual Clubroom account (OAuth 2, authorization code with PKCE).
        It asks them for the scopes it needs, and they can take its access back at any time.
      </p>
      {secret && (
        <div className="space-y-2 rounded-lg border p-3">
          <p className="font-medium">The client secret of “{secret.app?.name}”. Copy it now: it won't be shown again.</p>
          <div className="flex gap-2">
            <Input aria-label="Client secret" readOnly value={secret.clientSecret ?? ""} className="font-mono text-xs"
                   onFocus={(e) => e.target.select()} />
            <Button variant="outline" onClick={() => copy(secret.clientSecret ?? "")}><CopyIcon aria-hidden="true" /> Copy</Button>
          </div>
          <p className="text-muted-foreground">Keep it on your server only, never in a browser or a mobile app.</p>
        </div>
      )}
      {apps?.length === 0 && <p>You have no apps.</p>}
      <ul aria-label="Your apps" className="space-y-2">
        {apps?.map((app) => (
          <li key={app.id} className="space-y-2 rounded-lg border p-3">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <p className="flex flex-wrap items-center gap-2 font-medium">
                {app.name}
                <Badge variant="outline">{app.confidential ? "Confidential" : "Public"}</Badge>
              </p>
              <div className="flex flex-wrap gap-1">
                <Button variant="ghost" size="sm" onClick={() => setEditing(app)}>Edit</Button>
                {app.confidential && <Button variant="ghost" size="sm" onClick={() => setRenewing(app)}>New secret</Button>}
                <Button variant="ghost" size="sm" onClick={() => setDeleting(app)}>Delete</Button>
              </div>
            </div>
            {app.description && <p className="text-muted-foreground">{app.description}</p>}
            <p className="flex flex-wrap items-center gap-2 text-xs">
              Client ID <code className="rounded bg-muted px-1">{app.clientId}</code>
              <Button variant="ghost" size="sm" aria-label={`Copy the client ID of ${app.name}`} onClick={() => copy(app.clientId ?? "")}>
                <CopyIcon aria-hidden="true" />
              </Button>
            </p>
            <p className="text-xs text-muted-foreground">Redirects to {(app.redirectUris ?? []).join(", ")}</p>
          </li>
        ))}
      </ul>
      <Button disabled={full} onClick={() => setEditing("new")}><PlusIcon aria-hidden="true" /> Register an app</Button>
      {full && <p className="text-muted-foreground">You have {MAX_APPS} apps: delete one to register another.</p>}
      {editing && (
        <AppDialog app={editing === "new" ? undefined : editing} onClose={() => setEditing(null)}
                   onRegistered={(registered) => {
                     setEditing(null);
                     setSecret(registered.clientSecret ? registered : null);
                   }} />
      )}
      <ConfirmDialog open={renewing !== null} title={`A new secret for ${renewing?.name ?? ""}?`} confirmLabel="New secret"
                     description="The current secret stops working at once: put the new one on your server straight away."
                     onConfirm={async () => setSecret(await newSecret.mutateAsync(renewing?.id ?? ""))}
                     onClose={() => setRenewing(null)} />
      <ConfirmDialog open={deleting !== null} title={`Delete ${deleting?.name ?? ""}?`} confirmLabel="Delete"
                     description="Nobody can sign in to it any more, and everyone's access through it ends. This can't be undone."
                     onConfirm={async () => {
                       await remove.mutateAsync(deleting?.id ?? "");
                       if (secret?.app?.id === deleting?.id) setSecret(null);
                     }}
                     onClose={() => setDeleting(null)} />
    </div>
  );
}
