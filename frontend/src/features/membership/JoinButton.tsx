import { CheckIcon, ChevronDownIcon, LogOutIcon } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate, useSearchParams } from "react-router";
import { toast } from "sonner";
import { useMe } from "@/features/auth";
import { Button, buttonVariants } from "@/shared/ui/button";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/shared/ui/dialog";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/shared/ui/dropdown-menu";
import { useJoinClub, useLeaveClub, useMyMembership } from "./api";

export interface JoinableClub {
  id: string;
  name: string;
  status: string;
  joinPolicy: string;
}

/**
 * The club header's main action (08-frontend §11.1): "Join club" (OPEN) or "Request to join" (APPROVAL),
 * "Request sent" while a request waits, and for members a menu with "Leave club".
 * A link to /c/<slug>?intent=join joins as soon as the page opens, so a join started before logging in resumes.
 */
export function JoinButton({ club }: { club: JoinableClub }) {
  const { data: me } = useMe();
  const navigate = useNavigate();
  const location = useLocation();
  const [params, setParams] = useSearchParams();
  const { data: membership } = useMyMembership(club.id, !!me);
  const join = useJoinClub(club.id);
  const [leaving, setLeaving] = useState(false);
  const intentHandled = useRef(false);

  const joinable = club.status === "ACTIVE" && !!membership && !membership.member && !membership.pendingRequest;
  const wantsToJoin = params.get("intent") === "join";

  useEffect(() => {
    if (!wantsToJoin || !membership || intentHandled.current) return;
    intentHandled.current = true;
    const rest = new URLSearchParams(params);
    rest.delete("intent");
    setParams(rest, { replace: true });
    if (joinable) {
      join.mutate(undefined, joinFeedback(club.name));
    }
  }, [wantsToJoin, membership, params, setParams, joinable, join, club.name]);

  if (!me) {
    const returnTo = encodeURIComponent(`${location.pathname}?intent=join`);
    return <Button onClick={() => navigate(`/login?returnTo=${returnTo}`)}>Log in to join</Button>;
  }
  if (!membership || club.status !== "ACTIVE") return null;

  const action = membership.member ? (
    <DropdownMenu>
      <DropdownMenuTrigger className={buttonVariants({ variant: "secondary" })}>
        <CheckIcon /> Member <ChevronDownIcon />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem variant="destructive" onClick={() => setLeaving(true)}>
          <LogOutIcon /> Leave club
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  ) : membership.pendingRequest ? (
    <Button variant="secondary" disabled>Request sent</Button>   // E07-S03 adds "Cancel request"
  ) : (
    <Button disabled={join.isPending} onClick={() => join.mutate(undefined, joinFeedback(club.name))}>
      {club.joinPolicy === "OPEN" ? "Join club" : "Request to join"}
    </Button>
  );
  return (
    <>
      {action}
      {/* Outside the member branch: leaving switches the button to "Join club", and the dialog must stay mounted
          until the leave request has finished, or its success callback (toast, close) would never run. */}
      <LeaveDialog club={club} open={leaving} onClose={() => setLeaving(false)} />
    </>
  );
}

/** The same toasts whether the join comes from a click or from ?intent=join. */
function joinFeedback(clubName: string) {
  return {
    onSuccess: (outcome: { status?: string }) =>
      toast.success(outcome.status === "JOINED" ? `Welcome to ${clubName}!` : "Request sent. The owners will review it."),
    onError: (error: Error) => toast.error(error.message),
  };
}

function LeaveDialog({ club, open, onClose }: { club: JoinableClub; open: boolean; onClose: () => void }) {
  const leave = useLeaveClub(club.id);
  function onConfirm() {
    leave.mutate(undefined, {
      onSuccess: () => {
        toast.success(`You left ${club.name}.`);
        onClose();
      },
      onError: (error) => {
        toast.error(error.message);                    // e.g. LAST_OWNER: "Invite a co-owner first."
        onClose();
      },
    });
  }
  return (
    <Dialog open={open} onOpenChange={(next) => { if (!next) onClose(); }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Leave {club.name}?</DialogTitle>
          <DialogDescription>
            {club.joinPolicy === "OPEN" ? "You can join again at any time." : "To come back you will have to request to join again."}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button variant="destructive" disabled={leave.isPending} onClick={onConfirm}>Leave club</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
