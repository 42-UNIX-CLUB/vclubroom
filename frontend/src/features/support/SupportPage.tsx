import { InboxIcon, PlusIcon } from "lucide-react";
import { Link } from "react-router";
import { formatRelative } from "@/shared/lib/dates";
import { buttonVariants } from "@/shared/ui/button";
import { EmptyState } from "@/shared/ui/EmptyState";
import { PageHeader } from "@/shared/ui/PageHeader";
import { Skeleton } from "@/shared/ui/skeleton";
import { useMyTickets } from "./api";
import { TicketStatusBadge } from "./TicketStatusBadge";
import { typeLabel } from "./ticketTypes";

/** /support: my tickets, newest first (E17-S02). */
export function SupportPage() {
  const { data: tickets, isPending, isError } = useMyTickets();
  const newTicket = <Link to="/support/new" className={buttonVariants()}><PlusIcon aria-hidden="true" /> New ticket</Link>;

  return (
    <>
      <PageHeader title="Help & feedback" description="Report a problem, suggest an idea or ask a question. The team answers here."
                  actions={newTicket} />
      <div className="max-w-3xl space-y-3">
        {isPending && <Skeleton className="h-20" />}
        {isError && <p className="text-sm text-destructive">Could not load your tickets.</p>}
        {tickets?.length === 0 && (
          <EmptyState icon={InboxIcon} title="No tickets yet" description="Tickets you send appear here, with the team's answers." />
        )}
        <ul className="space-y-3">
          {tickets?.map((ticket) => (
            <li key={ticket.number}>
              <Link to={`/support/${ticket.number}`} className="block space-y-1 rounded-lg border p-3 hover:bg-muted/50">
                <span className="flex items-center justify-between gap-2">
                  <span className="font-medium">{ticket.title}</span>
                  {ticket.status && <TicketStatusBadge status={ticket.status} />}
                </span>
                <span className="block text-xs text-muted-foreground">
                  {ticket.ref} · {typeLabel(ticket.type)}{ticket.createdAt && ` · sent ${formatRelative(ticket.createdAt)}`}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      </div>
    </>
  );
}
