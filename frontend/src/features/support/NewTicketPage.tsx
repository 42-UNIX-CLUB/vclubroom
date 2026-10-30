import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { toast } from "sonner";
import { cn } from "@/shared/lib/utils";
import { Button, buttonVariants } from "@/shared/ui/button";
import { Field, FieldDescription, FieldLabel } from "@/shared/ui/field";
import { Input } from "@/shared/ui/input";
import { PageHeader } from "@/shared/ui/PageHeader";
import { Textarea } from "@/shared/ui/textarea";
import { type TicketType, useSubmitTicket } from "./api";
import { TICKET_TYPES } from "./ticketTypes";

/** A page of this app, from ?from= (the user menu sets it): anything else is ignored. */
function appPath(value: string | null): string | null {
  const ok = value?.startsWith("/") && !value.startsWith("//") && !value.startsWith("/\\") && !value.startsWith("/support");
  return ok ? value : null;
}

/** /support/new (E17-S02): what it's about, a title and the details; the page it came from, if wanted. */
export function NewTicketPage() {
  const [params] = useSearchParams();
  const from = appPath(params.get("from"));
  const navigate = useNavigate();
  const submit = useSubmitTicket();
  const [type, setType] = useState<TicketType | null>(null);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [includePage, setIncludePage] = useState(true);
  const chosen = TICKET_TYPES.find((t) => t.type === type);

  function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (!type) return;
    submit.mutate({
      type, title, description,
      pageUrl: from && includePage ? from : undefined,
      userAgent: chosen?.withBrowser ? navigator.userAgent : undefined,
    }, {
      onSuccess: (ticket) => {
        toast.success(`Ticket ${ticket.ref} sent. The team answers on its page.`);
        navigate(`/support/${ticket.number}`);
      },
      onError: (error) => toast.error(error.message),
    });
  }

  return (
    <>
      <PageHeader title="New ticket" description="Tell the team what's going on. You'll get their answers here and by email."
                  actions={<Link to="/support" className={buttonVariants({ variant: "outline" })}>My tickets</Link>} />
      <form onSubmit={onSubmit} className="max-w-3xl space-y-6">
        <fieldset className="space-y-3">
          <legend className="mb-3 text-sm font-medium">What is it about?</legend>
          <div className="grid gap-2 sm:grid-cols-2">
            {TICKET_TYPES.map(({ type: value, label, description: hint, icon: Icon }) => (
              <label key={value} className={cn("flex cursor-pointer gap-3 rounded-lg border p-3 text-sm hover:bg-muted/50",
                type === value && "border-primary bg-muted/50")}>
                <input type="radio" name="type" className="mt-1 accent-primary" checked={type === value} onChange={() => setType(value)} />
                <Icon className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
                <span>
                  <span className="block font-medium">{label}</span>
                  <span className="block text-muted-foreground">{hint}</span>
                </span>
              </label>
            ))}
          </div>
        </fieldset>
        <Field>
          <FieldLabel htmlFor="ticket-title">Title</FieldLabel>
          <Input id="ticket-title" maxLength={120} value={title} onChange={(e) => setTitle(e.target.value)} />
          <FieldDescription>One line, for example “The Join button does nothing”.</FieldDescription>
        </Field>
        <Field>
          <FieldLabel htmlFor="ticket-description">Details</FieldLabel>
          <Textarea id="ticket-description" rows={7} maxLength={5000} value={description} onChange={(e) => setDescription(e.target.value)} />
          <FieldDescription>
            {type === "BUG" ? "What you did, what you expected, and what happened instead." : "Everything the team should know."}
          </FieldDescription>
        </Field>
        {from && (
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" className="accent-primary" checked={includePage} onChange={(e) => setIncludePage(e.target.checked)} />
            Include the page I was on: <code className="text-xs">{from}</code>
          </label>
        )}
        {chosen?.withBrowser && <p className="text-sm text-muted-foreground">Your browser's name and version are sent with it.</p>}
        <Button type="submit" disabled={!type || !title.trim() || !description.trim() || submit.isPending}>Send ticket</Button>
      </form>
    </>
  );
}
