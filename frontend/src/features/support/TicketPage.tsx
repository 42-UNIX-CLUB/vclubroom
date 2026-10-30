import { ArrowLeftIcon } from "lucide-react";
import { useState } from "react";
import { Link, useParams } from "react-router";
import { toast } from "sonner";
import { formatDateTime } from "@/shared/lib/dates";
import { cn } from "@/shared/lib/utils";
import { Button } from "@/shared/ui/button";
import { EmptyState } from "@/shared/ui/EmptyState";
import { Field, FieldLabel } from "@/shared/ui/field";
import { Markdown } from "@/shared/ui/Markdown";
import { PageHeader } from "@/shared/ui/PageHeader";
import { Skeleton } from "@/shared/ui/skeleton";
import { Textarea } from "@/shared/ui/textarea";
import { type Message, useReply, useSupportLive, useTicket } from "./api";
import { TicketStatusBadge } from "./TicketStatusBadge";
import { typeLabel } from "./ticketTypes";

/** One message: the team's answers render their Markdown; mine show as typed. */
function MessageItem({ message }: { message: Message }) {
  const team = message.author === "TEAM";
  return (
    <li className={cn("space-y-1 rounded-lg border p-3", team && "border-primary/40 bg-muted/40")}>
      <p className="text-xs text-muted-foreground">
        <span className="font-medium text-foreground">{team ? "Support team" : "You"}</span>
        {message.createdAt && ` · ${formatDateTime(message.createdAt)}`}
        {!team && !message.delivered && " · on its way to the team"}
      </p>
      {team ? <Markdown>{message.body ?? ""}</Markdown> : <p className="whitespace-pre-wrap text-sm">{message.body}</p>}
    </li>
  );
}

/** /support/:number (E17-S02, E17-S03): one of my tickets and its conversation with the team. */
export function TicketPage() {
  const number = Number(useParams().number);
  const { data: ticket, isPending, isError } = useTicket(number);
  const reply = useReply(number);
  const [body, setBody] = useState("");
  useSupportLive();

  if (isPending) return <Skeleton className="h-48 max-w-3xl" />;
  if (isError || !ticket) {
    return <EmptyState title="Ticket not found" description="It doesn't exist, or it isn't yours."
                       action={<Link to="/support" className="text-sm underline">My tickets</Link>} />;
  }

  function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    reply.mutate(body, {
      onSuccess: () => setBody(""),
      onError: (error) => toast.error(error.message),
    });
  }

  return (
    <>
      <Link to="/support" className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeftIcon className="size-4" aria-hidden="true" /> My tickets
      </Link>
      <PageHeader title={ticket.title ?? ticket.ref ?? "Ticket"}
                  description={`${ticket.ref} · ${typeLabel(ticket.type)}${ticket.createdAt ? ` · sent ${formatDateTime(ticket.createdAt)}` : ""}`}
                  actions={ticket.status && <TicketStatusBadge status={ticket.status} />} />
      <div className="max-w-3xl space-y-6">
        <div className="space-y-3">
          <p className="whitespace-pre-wrap rounded-lg border p-4 text-sm">{ticket.description}</p>
          {ticket.pageUrl && <p className="text-sm text-muted-foreground">Page: <Link to={ticket.pageUrl} className="underline">{ticket.pageUrl}</Link></p>}
        </div>
        {ticket.status === "WAITING_ON_REPORTER" && (
          <p role="status" className="rounded-lg border border-destructive/40 p-3 text-sm">The team is waiting for your answer.</p>
        )}
        <section aria-labelledby="conversation-heading" className="space-y-3">
          <h2 id="conversation-heading" className="text-lg font-semibold">Conversation</h2>
          {ticket.messages?.length === 0 && <p className="text-sm text-muted-foreground">No answers yet. You'll be notified when the team writes.</p>}
          <ul className="space-y-3">
            {ticket.messages?.map((m) => <MessageItem key={m.id} message={m} />)}
          </ul>
          {ticket.canReply ? (
            <form onSubmit={onSubmit} className="space-y-2">
              <Field>
                <FieldLabel htmlFor="reply">Your reply</FieldLabel>
                <Textarea id="reply" rows={4} maxLength={5000} value={body} onChange={(e) => setBody(e.target.value)} />
              </Field>
              <Button type="submit" disabled={!body.trim() || reply.isPending}>Send reply</Button>
            </form>
          ) : (
            <p className="text-sm text-muted-foreground">
              This ticket is closed. <Link to="/support/new" className="underline">Send a new one</Link> if you need more help.
            </p>
          )}
        </section>
      </div>
    </>
  );
}
