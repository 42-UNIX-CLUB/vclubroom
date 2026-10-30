import { ArrowLeftIcon } from "lucide-react";
import { Link, useParams } from "react-router";
import { formatDateTime } from "@/shared/lib/dates";
import { EmptyState } from "@/shared/ui/EmptyState";
import { PageHeader } from "@/shared/ui/PageHeader";
import { Skeleton } from "@/shared/ui/skeleton";
import { useTicket } from "./api";
import { TicketStatusBadge } from "./TicketStatusBadge";
import { typeLabel } from "./ticketTypes";

/** /support/:number (E17-S02): one of my tickets. */
export function TicketPage() {
  const number = Number(useParams().number);
  const { data: ticket, isPending, isError } = useTicket(number);

  if (isPending) return <Skeleton className="h-48 max-w-3xl" />;
  if (isError || !ticket) {
    return <EmptyState title="Ticket not found" description="It doesn't exist, or it isn't yours."
                       action={<Link to="/support" className="text-sm underline">My tickets</Link>} />;
  }
  return (
    <>
      <Link to="/support" className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeftIcon className="size-4" aria-hidden="true" /> My tickets
      </Link>
      <PageHeader title={ticket.title ?? ticket.ref ?? "Ticket"}
                  description={`${ticket.ref} · ${typeLabel(ticket.type)}${ticket.createdAt ? ` · sent ${formatDateTime(ticket.createdAt)}` : ""}`}
                  actions={ticket.status && <TicketStatusBadge status={ticket.status} />} />
      <div className="max-w-3xl space-y-3">
        <p className="whitespace-pre-wrap rounded-lg border p-4 text-sm">{ticket.description}</p>
        {ticket.pageUrl && <p className="text-sm text-muted-foreground">Page: <Link to={ticket.pageUrl} className="underline">{ticket.pageUrl}</Link></p>}
      </div>
    </>
  );
}
