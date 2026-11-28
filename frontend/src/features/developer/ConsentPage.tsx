import { useState } from "react";
import { useSearchParams } from "react-router";
import { loadPage } from "@/shared/lib/loadPage";
import { Button } from "@/shared/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/shared/ui/card";
import { FullPageSpinner } from "@/shared/ui/FullPageSpinner";
import { answerConsent, useConsentRequest } from "./api";
import { describeScope } from "./scopes";

function hostOf(url: string): string {
  try {
    return new URL(url).host;
  } catch {
    return url;
  }
}

/**
 * /oauth/consent (E20-S05): an app asks to use Virtual Clubroom as you. The authorization server sends people here
 * with client_id, scope and state; the answer goes back to it, and from there to the app. Scopes allowed before stay
 * allowed: Cancel only declines the new ones.
 */
export function ConsentPage() {
  const [params] = useSearchParams();
  const clientId = params.get("client_id") ?? "";
  const state = params.get("state") ?? "";
  const { data: request, isPending, isError } = useConsentRequest(clientId, params.get("scope") ?? "");
  const [declined, setDeclined] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  if (!clientId || !state) {
    return <Notice>This page is part of signing in to an app. Start again from the app.</Notice>;
  }
  if (isPending) return <FullPageSpinner />;
  if (isError || !request) return <Notice>This app doesn't exist any more.</Notice>;

  const granted = request.granted ?? [];
  const scopes = request.scopes ?? [];
  const allowed = scopes.filter((s) => granted.includes(s) || !declined.includes(s));

  async function answer(chosen: string[]) {
    setBusy(true);
    setFailure(null);
    try {
      loadPage(await answerConsent(clientId, state, chosen));
    } catch (error) {
      setFailure((error as Error).message);
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-md py-10">
      <Card>
        <CardHeader>
          <CardTitle>{request.name} wants to use Virtual Clubroom as you</CardTitle>
          <CardDescription>
            Made by {request.ownerName}
            {request.homepageUrl && (
              <> · <a href={request.homepageUrl} target="_blank" rel="noopener noreferrer" className="underline">
                {hostOf(request.homepageUrl)}</a></>
            )}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4 text-sm">
          {request.description && <p>{request.description}</p>}
          <fieldset className="space-y-2">
            <legend className="mb-2 font-medium">It will be able to</legend>
            {scopes.map((scope) => {
              const about = describeScope(scope);
              const before = granted.includes(scope);
              return (
                <label key={scope} className="flex items-start gap-2">
                  <input type="checkbox" className="mt-1" checked={allowed.includes(scope)} disabled={before || busy}
                         onChange={(e) => setDeclined((d) => (e.target.checked ? d.filter((s) => s !== scope) : [...d, scope]))} />
                  <span>
                    <span className="font-medium">{about?.area}</span>: {about?.access}
                    {before && <span className="text-muted-foreground"> (allowed before)</span>}
                    <span className="block text-muted-foreground">{about?.description}</span>
                  </span>
                </label>
              );
            })}
          </fieldset>
          <p className="text-muted-foreground">
            It acts as you within these, never with admin powers, and never sees your password. You can take its
            access back at any time in Settings › Authorized apps.
          </p>
          {failure && <p role="alert" className="text-destructive">{failure}</p>}
        </CardContent>
        <CardFooter className="justify-end gap-2">
          <Button variant="ghost" disabled={busy} onClick={() => void answer([])}>Cancel</Button>
          <Button disabled={busy || allowed.length === 0} onClick={() => void answer(allowed)}>Allow</Button>
        </CardFooter>
      </Card>
    </div>
  );
}

function Notice({ children }: { children: string }) {
  return <p className="mx-auto max-w-md py-10 text-sm">{children}</p>;
}
