import { useState } from "react";
import { Button } from "@/shared/ui/button";
import { ConfirmDialog } from "@/shared/ui/ConfirmDialog";
import { type AuthorizedApp, useAuthorizedApps, useRevokeAuthorizedApp } from "./api";
import { describeScopes } from "./scopes";

/** Settings › Authorized apps (E20-S05): the apps you let use Virtual Clubroom as you, and taking that back. */
export function AuthorizedAppsPanel() {
  const { data: apps } = useAuthorizedApps();
  const revoke = useRevokeAuthorizedApp();
  const [revoking, setRevoking] = useState<AuthorizedApp | null>(null);

  return (
    <div className="space-y-3 text-sm">
      <p className="text-muted-foreground">Apps you signed in to with Virtual Clubroom, and what each may do as you.</p>
      {apps?.length === 0 && <p>You haven't let any app in.</p>}
      <ul aria-label="Authorized apps" className="space-y-2">
        {apps?.map((app) => (
          <li key={app.appId} className="flex flex-wrap items-start justify-between gap-2 rounded-lg border p-3">
            <div className="space-y-1">
              <p className="font-medium">
                {app.homepageUrl
                  ? <a href={app.homepageUrl} target="_blank" rel="noopener noreferrer" className="underline">{app.name}</a>
                  : app.name}
              </p>
              <p className="text-xs text-muted-foreground">{describeScopes(app.scopes ?? [])}</p>
            </div>
            <Button variant="ghost" size="sm" onClick={() => setRevoking(app)}>Remove access</Button>
          </li>
        ))}
      </ul>
      <ConfirmDialog open={revoking !== null} title={`Remove ${revoking?.name ?? ""}'s access?`} confirmLabel="Remove access"
                     description="It stops working as you at once. If you use it again, it asks you again."
                     onConfirm={async () => revoke.mutateAsync(revoking?.appId ?? "")}
                     onClose={() => setRevoking(null)} />
    </div>
  );
}
