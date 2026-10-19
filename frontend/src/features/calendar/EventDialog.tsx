import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import { MediaPicker } from "@/features/media";
import { useClubSpaces } from "@/features/spaces";
import { Button } from "@/shared/ui/button";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/shared/ui/dialog";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/shared/ui/field";
import { Input } from "@/shared/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/shared/ui/select";
import { Textarea } from "@/shared/ui/textarea";
import { type ClubEvent, useEditEvent, useScheduleEvent } from "./api";
import { emptyEventForm, eventFormSchema, eventToForm, toEditRequest, toEventRequest, type EventFormValues } from "./eventForm";

/** R-06: an event is seen by whoever can read its space. The hint says who that is before publishing. */
const AUDIENCE: Record<string, string> = {
  PUBLIC: "Everyone can see this event, members or not.",
  CLUB_MEMBERS: "Only club members can see this event.",
  GROUPS: "Only the groups that can read this space can see this event.",
};

/**
 * Schedule an event (no event given) or edit one (08-frontend §9.3). The space decides the audience and stays
 * fixed once the event exists; times are entered in Lisbon time, like every date in the app.
 */
export function EventDialog({ clubId, event, open, onOpenChange }: {
  clubId: string;
  event?: ClubEvent;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  // Mounted only while open, so each opening starts from fresh values.
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90svh] overflow-y-auto sm:max-w-lg">
        {open && <EventForm clubId={clubId} event={event} onDone={() => onOpenChange(false)} />}
      </DialogContent>
    </Dialog>
  );
}

function EventForm({ clubId, event, onDone }: { clubId: string; event?: ClubEvent; onDone: () => void }) {
  const navigate = useNavigate();
  const { data: spaces = [] } = useClubSpaces(clubId);
  const openSpaces = spaces.filter((space) => !space.archived);
  const schedule = useScheduleEvent(clubId);
  const edit = useEditEvent(event?.id ?? "");
  const [cover, setCover] = useState<{ ids: string[]; uploading: boolean }>({ ids: [], uploading: false });
  const form = useForm<EventFormValues>({
    resolver: zodResolver(eventFormSchema),
    defaultValues: event ? eventToForm(event) : emptyEventForm(openSpaces[0]?.id ?? ""),
    mode: "onTouched",
  });
  const { errors } = form.formState;
  const spaceId = form.watch("spaceId");
  const readLevel = spaces.find((space) => space.id === spaceId)?.readLevel;
  const firstSpaceId = openSpaces[0]?.id;
  useEffect(() => {                                       // the spaces may arrive after the dialog opened
    if (!event && firstSpaceId && !form.getValues("spaceId")) form.setValue("spaceId", firstSpaceId);
  }, [event, firstSpaceId, form]);

  const submit = form.handleSubmit(async (values) => {
    try {
      if (event) {
        await edit.mutateAsync(toEditRequest(values));
        toast.success("Event updated.");
        onDone();
      } else {
        const created = await schedule.mutateAsync(toEventRequest(values, cover.ids[0]));
        toast.success("Event scheduled.");
        onDone();
        void navigate(`/events/${created.id}`);
      }
    } catch (error) {
      form.setError("root", { message: error instanceof Error ? error.message : "Saving failed. Please try again." });
    }
  });

  return (
    <form onSubmit={submit} noValidate className="space-y-4">
      <DialogHeader>
        <DialogTitle>{event ? "Edit event" : "New event"}</DialogTitle>
        <DialogDescription>Times are in Lisbon time.</DialogDescription>
      </DialogHeader>
      <FieldGroup>
        <Controller
          control={form.control}
          name="spaceId"
          render={({ field, fieldState }) => (
            <Field data-invalid={fieldState.invalid}>
              <FieldLabel htmlFor="event-space">Space</FieldLabel>
              <Select
                value={field.value || null}
                onValueChange={(value) => field.onChange(value ?? "")}
                items={openSpaces.map((space) => ({ value: space.id ?? "", label: space.name ?? "" }))}
                disabled={!!event}
              >
                <SelectTrigger id="event-space" className="w-full" aria-invalid={fieldState.invalid} onBlur={field.onBlur}>
                  <SelectValue placeholder="Choose a space" />
                </SelectTrigger>
                <SelectContent>
                  {openSpaces.map((space) => <SelectItem key={space.id} value={space.id ?? ""}>{space.name}</SelectItem>)}
                </SelectContent>
              </Select>
              {readLevel && <FieldDescription>{AUDIENCE[readLevel]}</FieldDescription>}
              <FieldError errors={[fieldState.error]} />
            </Field>
          )}
        />
        <Field data-invalid={!!errors.title}>
          <FieldLabel htmlFor="event-title">Title</FieldLabel>
          <Input id="event-title" {...form.register("title")} />
          <FieldError errors={[errors.title]} />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field data-invalid={!!errors.startsAt}>
            <FieldLabel htmlFor="event-starts">Starts</FieldLabel>
            <Input id="event-starts" type="datetime-local" {...form.register("startsAt")} />
            <FieldError errors={[errors.startsAt]} />
          </Field>
          <Field data-invalid={!!errors.endsAt}>
            <FieldLabel htmlFor="event-ends">Ends</FieldLabel>
            <Input id="event-ends" type="datetime-local" {...form.register("endsAt")} />
            <FieldError errors={[errors.endsAt]} />
          </Field>
        </div>
        <Field data-invalid={!!errors.location}>
          <FieldLabel htmlFor="event-location">Where</FieldLabel>
          <Input id="event-location" placeholder="Cluster 2, the cafeteria…" {...form.register("location")} />
          <FieldError errors={[errors.location]} />
        </Field>
        <Field data-invalid={!!errors.onlineUrl}>
          <FieldLabel htmlFor="event-url">Online link</FieldLabel>
          <Input id="event-url" type="url" placeholder="https://…" {...form.register("onlineUrl")} />
          <FieldError errors={[errors.onlineUrl]} />
        </Field>
        <Field data-invalid={!!errors.description}>
          <FieldLabel htmlFor="event-description">Description</FieldLabel>
          <Textarea id="event-description" rows={3} {...form.register("description")} />
          <FieldDescription>Markdown works.</FieldDescription>
          <FieldError errors={[errors.description]} />
        </Field>
        {!event && (
          <Field>
            <FieldLabel>Cover image</FieldLabel>
            <MediaPicker purpose="EVENT_COVER" onChange={(ids, uploading) => setCover({ ids, uploading })} />
          </Field>
        )}
      </FieldGroup>
      <FieldError errors={[errors.root]} />
      <DialogFooter>
        <Button type="button" variant="ghost" onClick={onDone}>Cancel</Button>
        <Button type="submit" disabled={form.formState.isSubmitting || cover.uploading}>
          {cover.uploading ? "Uploading…" : event ? "Save changes" : "Schedule event"}
        </Button>
      </DialogFooter>
    </form>
  );
}
