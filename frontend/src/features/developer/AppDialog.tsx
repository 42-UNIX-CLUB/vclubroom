import { type FormEvent, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/shared/ui/button";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/shared/ui/dialog";
import { Field, FieldDescription, FieldLabel } from "@/shared/ui/field";
import { Input } from "@/shared/ui/input";
import { Textarea } from "@/shared/ui/textarea";
import { type App, type RegisteredApp, useRegisterApp, useUpdateApp } from "./api";

/**
 * Registering an OAuth app, or editing one (E20-S04). What people see when asked to let it in: its name, what it is
 * for, its homepage. Its kind is chosen once: confidential apps keep a secret on a server; public ones can't.
 */
export function AppDialog({ app, onClose, onRegistered }: {
  app?: App;
  onClose: () => void;
  onRegistered: (registered: RegisteredApp) => void;
}) {
  const register = useRegisterApp();
  const update = useUpdateApp();
  const [name, setName] = useState(app?.name ?? "");
  const [description, setDescription] = useState(app?.description ?? "");
  const [homepageUrl, setHomepageUrl] = useState(app?.homepageUrl ?? "");
  const [redirectUris, setRedirectUris] = useState((app?.redirectUris ?? []).join("\n"));
  const [confidential, setConfidential] = useState(app?.confidential ?? true);
  const uris = redirectUris.split("\n").map((u) => u.trim()).filter(Boolean);
  const busy = register.isPending || update.isPending;

  async function submit(event: FormEvent) {
    event.preventDefault();
    const input = { name: name.trim(), description, homepageUrl, redirectUris: uris, confidential };
    try {
      if (app?.id) {
        await update.mutateAsync({ appId: app.id, ...input });
        onClose();
      } else {
        onRegistered(await register.mutateAsync(input));
      }
    } catch (error) {
      toast.error((error as Error).message);
    }
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{app ? `Edit ${app.name}` : "Register an app"}</DialogTitle>
          <DialogDescription>People see these when your app asks to use Virtual Clubroom as them.</DialogDescription>
        </DialogHeader>
        <form onSubmit={(e) => void submit(e)} className="space-y-4">
          <Field>
            <FieldLabel htmlFor="app-name">Name</FieldLabel>
            <Input id="app-name" maxLength={80} value={name} onChange={(e) => setName(e.target.value)} />
          </Field>
          <Field>
            <FieldLabel htmlFor="app-description">What it does</FieldLabel>
            <Textarea id="app-description" maxLength={500} rows={2} value={description} onChange={(e) => setDescription(e.target.value)} />
          </Field>
          <Field>
            <FieldLabel htmlFor="app-homepage">Homepage</FieldLabel>
            <Input id="app-homepage" type="url" placeholder="https://" value={homepageUrl} onChange={(e) => setHomepageUrl(e.target.value)} />
          </Field>
          <Field>
            <FieldLabel htmlFor="app-redirects">Redirect URIs</FieldLabel>
            <Textarea id="app-redirects" rows={3} className="font-mono text-xs" value={redirectUris}
                      placeholder="https://myapp.example/callback" onChange={(e) => setRedirectUris(e.target.value)} />
            <FieldDescription>
              One per line, up to five: where people come back after signing in. https://, http://localhost while you
              develop, or your mobile app's own scheme (com.example.app:/callback).
            </FieldDescription>
          </Field>
          {!app && (
            <fieldset className="space-y-1.5 text-sm">
              <legend className="font-medium">Kind</legend>
              <label className="flex items-start gap-2">
                <input type="radio" name="app-kind" className="mt-1" checked={confidential} onChange={() => setConfidential(true)} />
                <span><span className="font-medium">Confidential</span>: runs on a server that keeps a secret.</span>
              </label>
              <label className="flex items-start gap-2">
                <input type="radio" name="app-kind" className="mt-1" checked={!confidential} onChange={() => setConfidential(false)} />
                <span><span className="font-medium">Public</span>: runs in a browser or on a phone, where a secret can't be kept.</span>
              </label>
            </fieldset>
          )}
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
            <Button type="submit" disabled={name.trim() === "" || uris.length === 0 || busy}>{app ? "Save" : "Register"}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
