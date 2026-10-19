import { keepPreviousData, type QueryClient, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type ClubEvent = components["schemas"]["EventView"];
export type ScheduleEventInput = components["schemas"]["ScheduleEventRequest"];
export type EditEventInput = components["schemas"]["EditEventRequest"];
export type CalendarItem = components["schemas"]["CalendarItem"];
export type CalendarRange = { from: string; to: string };

/** Query keys of this feature (08-frontend §5.1): every calendar view lives under ["calendar"]. */
export const calendarKeys = {
  event: (eventId: string) => ["event", eventId] as const,
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

/** After any change to an event, its page shows the answer and every calendar asks again. */
function eventChanged(queryClient: QueryClient, event: ClubEvent) {
  queryClient.setQueryData(calendarKeys.event(event.id ?? ""), event);
  return queryClient.invalidateQueries({ queryKey: calendarKeys.all });
}

export function useEvent(eventId: string) {
  return useQuery({
    queryKey: calendarKeys.event(eventId),
    queryFn: async () => (await api.GET("/api/events/{eventId}", { params: { path: { eventId } } })).data as ClubEvent,
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
