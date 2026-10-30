import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type Ticket = components["schemas"]["TicketView"];
export type TicketType = components["schemas"]["CreateTicketRequest"]["type"];
export type TicketStatus = NonNullable<Ticket["status"]>;
export type NewTicket = components["schemas"]["CreateTicketRequest"];

/** Query keys of this feature (08-frontend §5.1): ["support", "tickets"] and ["support", "tickets", number]. */
export const supportKeys = {
  all: ["support"] as const,
  mine: ["support", "tickets"] as const,
  ticket: (number: number) => ["support", "tickets", number] as const,
};

/** My tickets, newest first. */
export function useMyTickets() {
  return useQuery({
    queryKey: supportKeys.mine,
    queryFn: async () => (await api.GET("/api/support/tickets")).data ?? [],
  });
}

export function useTicket(number: number) {
  return useQuery({
    queryKey: supportKeys.ticket(number),
    queryFn: async () => (await api.GET("/api/support/tickets/{number}", { params: { path: { number } } })).data as Ticket,
  });
}

export function useSubmitTicket() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (ticket: NewTicket) => (await api.POST("/api/support/tickets", { body: ticket })).data as Ticket,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: supportKeys.all }),
  });
}
