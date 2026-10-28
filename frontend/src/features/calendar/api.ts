import { keepPreviousData, type QueryClient, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type ClubEvent = components["schemas"]["EventView"];
export type ScheduleEventInput = components["schemas"]["ScheduleEventRequest"];
export type EditEventInput = components["schemas"]["EditEventRequest"];
export type CalendarItem = components["schemas"]["CalendarItem"];
export type RsvpStatus = NonNullable<ClubEvent["myRsvp"]>;
export type CalendarRange = { from: string; to: string };

/** Query keys of this feature (08-frontend §5.1): every calendar view lives under ["calendar"]. */
export const calendarKeys = {
  event: (eventId: string) => ["event", eventId] as const,
  attendees: (eventId: string) => ["event", eventId, "attendees"] as const,
  all: ["calendar"] as const,
  campus: (scope: "all" | "mine", range: CalendarRange | null) => ["calendar", scope, range] as const,
  club: (clubId: string, range: CalendarRange | null) => ["calendar", "club", clubId, range] as const,
};

/** The campus calendar for the visible range; the previous range stays on screen while the next one loads. */
export function useCampusCalendar(scope: "all" | "mine", range: CalendarRange | null) {
  return useQuery({
    queryKey: calendarKeys.campus(scope, range),
    queryFn: async () => (await api.GET("/api/calendar", { params: { query: { from: range!.from, to: range!.to, scope } } })).data ?? [],
    enabled: range !== null,
    placeholderData: keepPreviousData,
  });
}

export function useClubCalendar(clubId: string, range: CalendarRange | null) {
  return useQuery({
    queryKey: calendarKeys.club(clubId, range),
    queryFn: async () => (await api.GET("/api/clubs/{clubId}/events", {
      params: { path: { clubId }, query: { from: range!.from, to: range!.to } },
    })).data ?? [],
    enabled: !!clubId && range !== null,
    placeholderData: keepPreviousData,
  });
}

/** After any change to an event (or an answer), its page shows the answer and every calendar asks again. */
function eventChanged(queryClient: QueryClient, event: ClubEvent) {
  queryClient.setQueryData(calendarKeys.event(event.id ?? ""), event);
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: calendarKeys.attendees(event.id ?? "") }),
    queryClient.invalidateQueries({ queryKey: calendarKeys.all }),
  ]);
}

export function useEvent(eventId: string) {
  return useQuery({
    queryKey: calendarKeys.event(eventId),
    queryFn: async () => (await api.GET("/api/events/{eventId}", { params: { path: { eventId } } })).data as ClubEvent,
  });
}

/** Going and interested people, by name (readers of the event's space). */
export function useAttendees(eventId: string) {
  return useQuery({
    queryKey: calendarKeys.attendees(eventId),
    queryFn: async () => (await api.GET("/api/events/{eventId}/attendees", { params: { path: { eventId } } })).data ?? [],
  });
}

/** Answer (a status), or take the answer back (null). The server returns the event with its new counts. */
export function useRsvp(eventId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (status: RsvpStatus | null) => (status
      ? await api.PUT("/api/events/{eventId}/rsvp", { params: { path: { eventId } }, body: { status } })
      : await api.DELETE("/api/events/{eventId}/rsvp", { params: { path: { eventId } } })).data as ClubEvent,
    onSuccess: (event) => eventChanged(queryClient, event),
  });
}

export function useScheduleEvent(clubId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: ScheduleEventInput) =>
      (await api.POST("/api/clubs/{clubId}/events", { params: { path: { clubId } }, body })).data as ClubEvent,
    onSuccess: (event) => eventChanged(queryClient, event),
  });
}

export function useEditEvent(eventId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: EditEventInput) =>
      (await api.PATCH("/api/events/{eventId}", { params: { path: { eventId } }, body })).data as ClubEvent,
    onSuccess: (event) => eventChanged(queryClient, event),
  });
}

export function useCancelEvent(eventId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (reason: string) =>
      (await api.POST("/api/events/{eventId}/cancel", { params: { path: { eventId } }, body: { reason: reason || undefined } })).data as ClubEvent,
    onSuccess: (event) => eventChanged(queryClient, event),
  });
}

export type CalendarFeed = components["schemas"]["CalendarFeedResponse"];

/** My calendar feed (E11-S05), outside ["calendar"]: refreshing the calendars needn't ask about it again. */
export const feedKeys = { mine: ["calendar-feed"] as const };

/** Whether I have a feed, and since when; its URL is never shown again after it was made. */
export function useCalendarFeed() {
  return useQuery({
    queryKey: feedKeys.mine,
    queryFn: async () => (await api.GET("/api/me/calendar-feed")).data ?? {},
  });
}

/** A new secret URL; an older one stops working. The answer is the only time the URL is seen. */
export function useNewCalendarFeed() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => (await api.POST("/api/me/calendar-feed")).data as CalendarFeed,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: feedKeys.mine }),
  });
}

export function useTurnOffCalendarFeed() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      await api.DELETE("/api/me/calendar-feed");
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: feedKeys.mine }),
  });
}
