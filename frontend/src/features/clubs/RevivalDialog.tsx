import { useState } from "react";
import { toast } from "sonner";
import { ApiError } from "@/shared/api/errors";
import { Button } from "@/shared/ui/button";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/shared/ui/dialog";
import { Field, FieldDescription, FieldLabel } from "@/shared/ui/field";
import { Textarea } from "@/shared/ui/textarea";
import { type Club, useRequestRevival } from "./api";

/**
 * "Request a revival" on an archived club (D-14): a motivation for the admins. If they approve, the club starts a
 * new era with you as its owner; its history stays as it is.
 */
export function RevivalDialog({ club }: { club: Club }) {
  const [open, setOpen] = useState(false);
  const [motivation, setMotivation] = useState("");
  const request = useRequestRevival(club.id!);

  function onOpenChange(next: boolean) {
    setOpen(next);
    if (!next) setMotivation("");
  }

  function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    request.mutate(motivation.trim(), {
      onSuccess: () => {
        toast.success("Request sent. You will be notified when an admin decides.");
        onOpenChange(false);
      },
      onError: (error) => toast.error(error instanceof ApiError && error.code === "REVIVAL_ALREADY_REQUESTED"
        ? "You already asked to revive this club. An admin will answer soon."
        : error.message),
    });
  }

  return (
    <>
      <Button variant="outline" size="sm" className="mt-2" onClick={() => setOpen(true)}>Request a revival</Button>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent>
          <form onSubmit={onSubmit} className="space-y-4">
            <DialogHeader>
              <DialogTitle>Revive {club.name}?</DialogTitle>
              <DialogDescription>
                If an admin agrees, the club starts era {(club.currentEra ?? 1) + 1} with you as its owner. Everything from
                earlier eras stays readable, as history.
              </DialogDescription>
            </DialogHeader>
            <Field>
              <FieldLabel htmlFor="revival-motivation">Why bring it back?</FieldLabel>
              <Textarea id="revival-motivation" rows={5} maxLength={2000} value={motivation}
                onChange={(event) => setMotivation(event.target.value)} />
              <FieldDescription>Who is with you, and what you plan to do. The admins read this.</FieldDescription>
            </Field>
            <DialogFooter>
              <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>Cancel</Button>
              <Button type="submit" disabled={!motivation.trim() || request.isPending}>Send request</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}
