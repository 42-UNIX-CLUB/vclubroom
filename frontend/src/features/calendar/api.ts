import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type ClubEvent = components["schemas"]["EventView"];
export type ScheduleEventInput = components["schemas"]["ScheduleEventRequest"];
export type EditEventInput = components["schemas"]["EditEventRequest"];

/** Query keys of this feature (08-frontend §5.1). E11-S02 adds the calendar ranges. */
export const calendarKeys = {
  event: (eventId: string) => ["event", eventId] as const,
};

export function useEvent(eventId: string) {
  return useQuery({
    queryKey: calendarKeys.event(eventId),
    queryFn: async () => (await api.GET("/api/events/{eventId}", { params: { path: { eventId } } })).data as ClubEvent,
  });
}

/** Every change answers the event as it is now: the page shows it without asking again. */
export function useScheduleEvent(clubId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: ScheduleEventInput) =>
      (await api.POST("/api/clubs/{clubId}/events", { params: { path: { clubId } }, body })).data as ClubEvent,
    onSuccess: (event) => queryClient.setQueryData(calendarKeys.event(event.id ?? ""), event),
  });
}

export function useEditEvent(eventId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: EditEventInput) =>
      (await api.PATCH("/api/events/{eventId}", { params: { path: { eventId } }, body })).data as ClubEvent,
    onSuccess: (event) => queryClient.setQueryData(calendarKeys.event(eventId), event),
  });
}

export function useCancelEvent(eventId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (reason: string) =>
      (await api.POST("/api/events/{eventId}/cancel", { params: { path: { eventId } }, body: { reason: reason || undefined } })).data as ClubEvent,
    onSuccess: (event) => queryClient.setQueryData(calendarKeys.event(eventId), event),
  });
}
