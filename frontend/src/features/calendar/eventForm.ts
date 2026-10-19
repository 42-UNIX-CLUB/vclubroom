import { z } from "zod";
import { fromLisbonInput, toLisbonInput } from "@/shared/lib/dates";
import type { ClubEvent, EditEventInput, ScheduleEventInput } from "./api";

const URL = /^https?:\/\/.+/;

/** Same limits as the backend's ScheduleEventRequest; times are datetime-local values in Lisbon time. */
export const eventFormSchema = z.object({
  spaceId: z.string().min(1, "Choose a space."),
  title: z.string().trim().min(1, "Give the event a title.").max(140, "Use at most 140 characters."),
  description: z.string().trim().max(5000, "Use at most 5000 characters."),
  location: z.string().trim().max(200, "Use at most 200 characters."),
  onlineUrl: z.string().trim().max(500, "Use at most 500 characters.")
    .refine((value) => value === "" || URL.test(value), "Use a link that starts with http:// or https://."),
  startsAt: z.string().min(1, "Choose when it starts."),
  endsAt: z.string().min(1, "Choose when it ends."),
}).refine((values) => !values.startsAt || !values.endsAt || values.endsAt > values.startsAt,   // same format: text order is time order
  { path: ["endsAt"], message: "The event must end after it starts." });

export type EventFormValues = z.input<typeof eventFormSchema>;

/** A new event: tomorrow 18:00–20:00 (Lisbon), in the first space. */
export function emptyEventForm(spaceId = "", now = new Date()): EventFormValues {
  const tomorrow = toLisbonInput(now.getTime() + 86_400_000).slice(0, 10);
  return { spaceId, title: "", description: "", location: "", onlineUrl: "", startsAt: `${tomorrow}T18:00`, endsAt: `${tomorrow}T20:00` };
}

export function eventToForm(event: ClubEvent): EventFormValues {
  return {
    spaceId: event.spaceId ?? "",
    title: event.title ?? "",
    description: event.description ?? "",
    location: event.location ?? "",
    onlineUrl: event.onlineUrl ?? "",
    startsAt: event.startsAt ? toLisbonInput(event.startsAt) : "",
    endsAt: event.endsAt ? toLisbonInput(event.endsAt) : "",
  };
}

export function toEventRequest(values: EventFormValues, coverMediaId?: string): ScheduleEventInput {
  return { spaceId: values.spaceId, ...toEditRequest(values), coverMediaId };
}

/** Editing sends every field again; the space and the cover stay as they are. */
export function toEditRequest(values: EventFormValues): EditEventInput {
  return {
    title: values.title.trim(),
    description: values.description.trim() || undefined,
    location: values.location.trim() || undefined,
    onlineUrl: values.onlineUrl.trim() || undefined,
    startsAt: fromLisbonInput(values.startsAt),
    endsAt: fromLisbonInput(values.endsAt),
  };
}
