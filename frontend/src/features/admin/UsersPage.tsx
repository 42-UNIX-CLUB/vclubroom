import { KeyRoundIcon, SearchIcon, UserPlusIcon, UsersIcon } from "lucide-react";
import { useDeferredValue, useState } from "react";
import { toast } from "sonner";
import { useMe } from "@/features/auth";
import { formatDate } from "@/shared/lib/dates";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { Card, CardContent } from "@/shared/ui/card";
import { ConfirmDialog } from "@/shared/ui/ConfirmDialog";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/shared/ui/dialog";
import { EmptyState } from "@/shared/ui/EmptyState";
import { Field, FieldLabel } from "@/shared/ui/field";
import { Input } from "@/shared/ui/input";
import { PageHeader } from "@/shared/ui/PageHeader";
import { Skeleton } from "@/shared/ui/skeleton";
import { Switch } from "@/shared/ui/switch";
import { type AdminUser, useAdminUsers, useCreateUser, useResetPassword, useRevokeUserTokens, useUpdateUser } from "./api";
import { temporaryPassword } from "./temporaryPassword";

type Confirm = { title: string; description: string; label: string; run: () => Promise<unknown> } | null;

/**
 * /admin/users (E15-S02): every account, searchable. Admins create local accounts (D-09), make or unmake admins,
 * disable or enable accounts, and reset local passwords. Each change logs that person out everywhere (BR-24).
 * Your own row has no actions: another admin changes your role or status.
 */
export function UsersPage() {
  const { data: me } = useMe();
  const [query, setQuery] = useState("");
  const { data, isPending } = useAdminUsers(useDeferredValue(query.trim()));
  const update = useUpdateUser();
  const revokeTokens = useRevokeUserTokens();
  const [creating, setCreating] = useState(false);
  const [resetting, setResetting] = useState<AdminUser | null>(null);
  const [confirm, setConfirm] = useState<Confirm>(null);

  function ask(user: AdminUser, change: { platformRole?: "USER" | "ADMIN"; status?: "ACTIVE" | "DISABLED" }, label: string) {
    const name = user.displayName ?? user.login;
    const [title, description] = change.status === "DISABLED"
      ? [`Disable ${name}?`, "They are logged out everywhere and can't log in until an admin enables the account. Their clubs and posts stay."]
      : change.platformRole === "ADMIN"
        ? [`Make ${name} an admin?`, "They are logged out everywhere, and have admin rights when they log in again."]
        : [`Remove ${name}'s admin rights?`, "They are logged out everywhere, and log in again as a regular user."];
    setConfirm({ title, description, label, run: () => update.mutateAsync({ userId: user.id ?? "", ...change }) });
  }

  return (
    <>
      <PageHeader title="Users" actions={<Button onClick={() => setCreating(true)}><UserPlusIcon aria-hidden="true" /> New local account</Button>} />
      <div className="relative mb-4 max-w-sm">
        <SearchIcon className="absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
        <Input aria-label="Search users" placeholder="Login or name" className="pl-8" value={query} onChange={(e) => setQuery(e.target.value)} />
      </div>
      {isPending && <Skeleton className="h-24" />}
      {data?.items?.length === 0 && <EmptyState icon={UsersIcon} title="Nobody matches" />}
      <ul aria-label="Users" className="space-y-2">
        {data?.items?.map((user) => {
          const self = user.id === me?.id;
          const disabled = user.status === "DISABLED";
          return (
            <li key={user.id}>
              <Card size="sm" className={disabled ? "opacity-70" : undefined}>
                <CardContent className="flex flex-wrap items-center justify-between gap-3">
                  <div className="min-w-0">
                    <p className="flex flex-wrap items-center gap-2 font-medium">
                      {user.displayName}
                      <span className="text-sm font-normal text-muted-foreground">@{user.login}</span>
                      <Badge variant="outline">{user.provider === "LOCAL" ? "Local" : "42"}</Badge>
                      {user.role === "ADMIN" && <Badge>Admin</Badge>}
                      {disabled && <Badge variant="destructive">Disabled</Badge>}
                      {self && <Badge variant="secondary">You</Badge>}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {user.email ?? "No email"} · Joined {user.createdAt && formatDate(user.createdAt)}
                      {user.lastLoginAt ? ` · Last login ${formatDate(user.lastLoginAt)}` : " · Never logged in"}
                    </p>
                  </div>
                  {!self && (
                    <div className="flex flex-wrap gap-1">
                      {user.role === "ADMIN"
                        ? <Button variant="outline" size="sm" onClick={() => ask(user, { platformRole: "USER" }, "Remove admin")}>Remove admin</Button>
                        : <Button variant="outline" size="sm" onClick={() => ask(user, { platformRole: "ADMIN" }, "Make admin")}>Make admin</Button>}
                      {disabled
                        ? <Button variant="outline" size="sm" onClick={() => update.mutate({ userId: user.id ?? "", status: "ACTIVE" }, { onError: (e) => toast.error(e.message) })}>Enable</Button>
                        : <Button variant="outline" size="sm" onClick={() => ask(user, { status: "DISABLED" }, "Disable account")}>Disable</Button>}
                      {user.provider === "LOCAL" && (
                        <Button variant="ghost" size="sm" onClick={() => setResetting(user)}><KeyRoundIcon aria-hidden="true" /> Reset password</Button>
                      )}
                      <Button variant="ghost" size="sm" onClick={() => setConfirm({
                        title: `Revoke ${user.displayName ?? user.login}'s tokens?`,
                        description: "Every personal access token they made stops working at once. Their scripts and apps need new ones.",
                        label: "Revoke tokens",
                        run: () => revokeTokens.mutateAsync(user.id ?? ""),
                      })}>Revoke tokens</Button>
                    </div>
                  )}
                </CardContent>
              </Card>
            </li>
          );
        })}
      </ul>
      {creating && <CreateAccountDialog onClose={() => setCreating(false)} />}
      {resetting && <ResetPasswordDialog user={resetting} onClose={() => setResetting(null)} />}
      <ConfirmDialog open={confirm !== null} title={confirm?.title ?? ""} description={confirm?.description ?? ""}
                     confirmLabel={confirm?.label ?? ""} onConfirm={() => confirm!.run()} onClose={() => setConfirm(null)} />
    </>
  );
}

/** A temporary password field: generated, shown, and copied with a click so the admin can pass it on. */
function PasswordField({ id, value, onChange }: { id: string; value: string; onChange: (value: string) => void }) {
  return (
    <Field>
      <FieldLabel htmlFor={id}>Temporary password</FieldLabel>
      <div className="flex gap-2">
        <Input id={id} value={value} onChange={(e) => onChange(e.target.value)} className="font-mono" />
        <Button type="button" variant="outline" onClick={() => onChange(temporaryPassword())}>Generate</Button>
        <Button type="button" variant="ghost" disabled={!value}
                onClick={() => void navigator.clipboard?.writeText(value).then(() => toast.success("Copied."))}>Copy</Button>
      </div>
      <p className="text-xs text-muted-foreground">At least 10 characters. They change it in Settings after logging in.</p>
    </Field>
  );
}

function CreateAccountDialog({ onClose }: { onClose: () => void }) {
  const create = useCreateUser();
  const [username, setUsername] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState(() => temporaryPassword());
  const [admin, setAdmin] = useState(false);

  function onCreate() {
    create.mutate({ username: username.trim(), displayName: displayName.trim(), email: email.trim() || undefined, temporaryPassword: password, admin }, {
      onSuccess: (user) => {
        toast.success(`${user.displayName} can now log in as ${user.login}.`);
        onClose();
      },
      onError: (error) => toast.error(error.message),
    });
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New local account</DialogTitle>
          <DialogDescription>For staff and partners without a 42 account. Give them the login and the temporary password.</DialogDescription>
        </DialogHeader>
        <Field>
          <FieldLabel htmlFor="new-username">Login</FieldLabel>
          <Input id="new-username" maxLength={30} autoComplete="off" value={username} onChange={(e) => setUsername(e.target.value.toLowerCase())} placeholder="jose.silva`" />
        </Field>
        <Field>
          <FieldLabel htmlFor="new-display-name">Name</FieldLabel>
          <Input id="new-display-name" maxLength={100} value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
        </Field>
        <Field>
          <FieldLabel htmlFor="new-email">Email (optional, for notifications)</FieldLabel>
          <Input id="new-email" type="email" maxLength={320} value={email} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <PasswordField id="new-password" value={password} onChange={setPassword} />
        <label className="flex items-center gap-3 text-sm">
          <Switch checked={admin} onCheckedChange={setAdmin} aria-label="Admin" /> Admin
        </label>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button disabled={!username.trim() || !displayName.trim() || password.length < 10 || create.isPending} onClick={onCreate}>Create account</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function ResetPasswordDialog({ user, onClose }: { user: AdminUser; onClose: () => void }) {
  const reset = useResetPassword();
  const [password, setPassword] = useState(() => temporaryPassword());

  function onReset() {
    reset.mutate({ userId: user.id ?? "", temporaryPassword: password }, {
      onSuccess: () => {
        toast.success(`${user.displayName} logs in with the new password.`);
        onClose();
      },
      onError: (error) => toast.error(error.message),
    });
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Reset {user.displayName}'s password</DialogTitle>
          <DialogDescription>They are logged out everywhere and log in with this temporary password.</DialogDescription>
        </DialogHeader>
        <PasswordField id="reset-password" value={password} onChange={setPassword} />
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button disabled={password.length < 10 || reset.isPending} onClick={onReset}>Set password</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
