import { CalendarPlusIcon, CopyIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { formatDate } from "@/shared/lib/dates";
import { Button } from "@/shared/ui/button";
import { ConfirmDialog } from "@/shared/ui/ConfirmDialog";
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger,
} from "@/shared/ui/dialog";
import { Input } from "@/shared/ui/input";
import { useCalendarFeed, useNewCalendarFeed, useTurnOffCalendarFeed } from "./api";

/**
 * My calendar feed (E11-S05, R-16): a secret link that calendar apps (Google, Apple, Outlook) subscribe to, showing
 * my clubs' events. The link is shown once, right after it is made; a new one replaces it, and turning it off ends it.
 */
export function CalendarFeedPanel() {
  const { data: feed } = useCalendarFeed();
  const create = useNewCalendarFeed();
  const turnOff = useTurnOffCalendarFeed();
  const [url, setUrl] = useState<string | null>(null);
  const [replacing, setReplacing] = useState(false);
  const [stopping, setStopping] = useState(false);
  const active = !!feed?.createdAt;

  async function makeLink() {
    const made = await create.mutateAsync();
    setUrl(made.url ?? null);
  }

  return (
    <div className="space-y-3 text-sm">
      <p className="text-muted-foreground">
        Add your clubs' events to Google Calendar, Apple Calendar or Outlook. They follow along as events are added,
        moved or cancelled (calendar apps refresh every few hours).
      </p>
      {url ? (
        <div className="space-y-2">
          <p className="font-medium">Your link. Copy it now: it won't be shown again.</p>
          <div className="flex gap-2">
            <Input aria-label="Calendar feed link" readOnly value={url} className="font-mono text-xs" onFocus={(e) => e.target.select()} />
            <Button variant="outline" onClick={() => void navigator.clipboard?.writeText(url).then(() => toast.success("Copied."))}>
              <CopyIcon aria-hidden="true" /> Copy
            </Button>
          </div>
          <p className="text-muted-foreground">
            In your calendar app, choose "Add calendar from URL" (or "Subscribe") and paste it, or{" "}
            <a className="underline" href={url.replace(/^https?:/, "webcal:")}>open it in your calendar app</a>.
            Anyone with this link sees your clubs' events: keep it to yourself.
          </p>
        </div>
      ) : active ? (
        <p>You have a calendar link, made on {formatDate(feed!.createdAt!)}. Lost it? Get a new one: the old one stops working.</p>
      ) : (
        <p>You have no calendar link yet.</p>
      )}
      <div className="flex flex-wrap gap-2">
        {!active && !url && (
          <Button disabled={create.isPending} onClick={() => void makeLink().catch((e: Error) => toast.error(e.message))}>
            <CalendarPlusIcon aria-hidden="true" /> Get my calendar link
          </Button>
        )}
        {active && <Button variant="outline" onClick={() => setReplacing(true)}>Get a new link</Button>}
        {active && <Button variant="ghost" onClick={() => setStopping(true)}>Turn off</Button>}
      </div>
      <ConfirmDialog open={replacing} onClose={() => setReplacing(false)} title="Get a new link?" confirmLabel="Get a new link"
                     description="Calendars subscribed to your current link stop updating. Subscribe again with the new one."
                     onConfirm={makeLink} />
      <ConfirmDialog open={stopping} onClose={() => setStopping(false)} title="Turn off your calendar link?" confirmLabel="Turn off"
                     description="Calendars subscribed to it stop updating. You can get a new link at any time."
                     onConfirm={async () => {
                       await turnOff.mutateAsync();
                       setUrl(null);
                     }} />
    </div>
  );
}

/** The same panel from the campus calendar, in a dialog. */
export function CalendarFeedButton() {
  return (
    <Dialog>
      <DialogTrigger render={<Button variant="outline" />}>
        <CalendarPlusIcon aria-hidden="true" /> Add to my calendar app
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Your calendar link</DialogTitle>
          <DialogDescription>Your clubs' events, in the calendar app you already use.</DialogDescription>
        </DialogHeader>
        <CalendarFeedPanel />
      </DialogContent>
    </Dialog>
  );
}
