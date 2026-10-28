import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import { api } from "@/shared/api/client";
import { Button } from "@/shared/ui/button";
import { Field, FieldLabel } from "@/shared/ui/field";
import { Input } from "@/shared/ui/input";

const MIN = 10;

/**
 * Change my password (local accounts, E15-S02): the current one, then the new one twice. This browser stays logged
 * in; the others are logged out.
 */
export function PasswordForm() {
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [again, setAgain] = useState("");
  const change = useMutation({
    mutationFn: async () => {
      await api.PUT("/api/me/password", { body: { currentPassword: current, newPassword: next } });
    },
  });
  const mismatch = again.length > 0 && again !== next;

  function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    change.mutate(undefined, {
      onSuccess: () => {
        toast.success("Password changed. Your other browsers are logged out.");
        setCurrent("");
        setNext("");
        setAgain("");
      },
      onError: (error) => toast.error(error.message),
    });
  }

  return (
    <form onSubmit={onSubmit} className="max-w-sm space-y-3">
      <Field>
        <FieldLabel htmlFor="current-password">Current password</FieldLabel>
        <Input id="current-password" type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} />
      </Field>
      <Field>
        <FieldLabel htmlFor="new-password">New password</FieldLabel>
        <Input id="new-password" type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} />
        <p className="text-xs text-muted-foreground">At least {MIN} characters.</p>
      </Field>
      <Field>
        <FieldLabel htmlFor="new-password-again">New password, again</FieldLabel>
        <Input id="new-password-again" type="password" autoComplete="new-password" value={again} aria-invalid={mismatch}
               onChange={(e) => setAgain(e.target.value)} />
        {mismatch && <p className="text-xs text-destructive">The two don't match.</p>}
      </Field>
      <Button type="submit" disabled={!current || next.length < MIN || next !== again || change.isPending}>Change password</Button>
    </form>
  );
}
