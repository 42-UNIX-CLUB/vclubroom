import { HistoryIcon } from "lucide-react";
import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { toast } from "sonner";
import { useMe } from "@/features/auth";
import { formatDateTime, formatTime } from "@/shared/lib/dates";
import { cn } from "@/shared/lib/utils";
import { Button, buttonVariants } from "@/shared/ui/button";
import { ClubAvatar } from "@/shared/ui/ClubAvatar";
import { ConfirmDialog } from "@/shared/ui/ConfirmDialog";
import { Skeleton } from "@/shared/ui/skeleton";
import { Textarea } from "@/shared/ui/textarea";
import { type ChatMessage, useChatLive, useChatMessages, useDeleteMessage, useEditMessage, useSendMessage } from "./api";
import { MessageComposer } from "./MessageComposer";
import { newNonce } from "./nonce";

/** What the chat needs from the channel the space module found (ChannelView). */
export interface ChatChannel {
  id?: string;
  canWrite?: boolean;
  canModerate?: boolean;
  currentEra?: number;
}

type Pending = { clientNonce: string; body: string; failed: boolean };

/**
 * A chat channel (08-frontend §9.3): history that grows upwards, live messages, and my own messages shown at once as
 * "sending", then replaced by the server's copy, matched by nonce, whichever arrives first, the answer or the push.
 * ?era=n shows an earlier era, read-only (BR-15).
 */
export function ChatRoom({ channel }: { channel: ChatChannel }) {
  const channelId = channel.id ?? "";
  const [params] = useSearchParams();
  const era = params.get("era") ? Number(params.get("era")) : null;
  const viewingHistory = era !== null && era !== channel.currentEra;
  const { data, isPending, hasNextPage, fetchNextPage, isFetchingNextPage } = useChatMessages(channelId, era);
  const send = useSendMessage(channelId);
  const { data: me } = useMe();
  const [pending, setPending] = useState<Pending[]>([]);
  // Screen readers hear other people's new messages (aria-live); my own are already on screen as I send them.
  const mine = useRef(new Set<string>());
  const [announcement, setAnnouncement] = useState("");
  useChatLive(viewingHistory ? "" : channelId, (message) => {
    if (!mine.current.has(message.clientNonce ?? "")) {
      setAnnouncement(`${message.author?.displayName ?? "Someone"}: ${message.body ?? ""}`);
    }
  });

  const messages = [...(data?.pages ?? [])].reverse().flatMap((page) => page.items ?? []);
  const known = new Set(messages.map((m) => m.clientNonce));
  const waiting = pending.filter((p) => !known.has(p.clientNonce));

  function deliver(item: Pending) {
    mine.current.add(item.clientNonce);
    setPending((list) => [...list.filter((p) => p.clientNonce !== item.clientNonce), { ...item, failed: false }]);
    send.mutate({ body: item.body, clientNonce: item.clientNonce }, {
      onError: (error) => {
        setPending((list) => list.map((p) => p.clientNonce === item.clientNonce ? { ...p, failed: true } : p));
        toast.error(error.message);
      },
    });
  }

  // Stay at the bottom when something new arrives, if the reader was already there; keep the reading position
  // when older messages are added on top.
  const scroller = useRef<HTMLDivElement>(null);
  const atBottom = useRef(true);
  const previousHeight = useRef(0);
  const pageCount = data?.pages.length ?? 0;
  const lastPageCount = useRef(0);
  useLayoutEffect(() => {
    const el = scroller.current;
    if (!el) return;
    if (pageCount > lastPageCount.current && lastPageCount.current > 0) {
      el.scrollTop += el.scrollHeight - previousHeight.current;          // older page added above
    } else if (atBottom.current) {
      el.scrollTop = el.scrollHeight;
    }
    lastPageCount.current = pageCount;
    previousHeight.current = el.scrollHeight;
  }, [messages.length, waiting.length, pageCount]);

  // Scrolling to the top loads the older page (the button does the same where IntersectionObserver is missing).
  const top = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!hasNextPage || !top.current || typeof IntersectionObserver === "undefined") return;
    const observer = new IntersectionObserver((entries) => {
      if (entries[0]?.isIntersecting && !isFetchingNextPage) {
        previousHeight.current = scroller.current?.scrollHeight ?? 0;
        void fetchNextPage();
      }
    });
    observer.observe(top.current);
    return () => observer.disconnect();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  return (
    <div className="space-y-3">
      <p aria-live="polite" className="sr-only">{announcement}</p>
      {viewingHistory && (
        <div role="status" className="flex flex-wrap items-center justify-between gap-2 rounded-xl border bg-muted/50 p-3 text-sm">
          <span className="flex items-center gap-2"><HistoryIcon className="size-4" aria-hidden="true" /> Era {era}: read-only history.</span>
          <Link to="?" className={buttonVariants({ variant: "outline", size: "sm" })}>Back to era {channel.currentEra}</Link>
        </div>
      )}
      <div
        ref={scroller}
        className="h-[60svh] space-y-3 overflow-y-auto rounded-xl border p-3"
        onScroll={(event) => {
          const el = event.currentTarget;
          atBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 40;
        }}
      >
        <div ref={top} />
        {hasNextPage && (
          <div className="flex justify-center">
            <Button variant="ghost" size="sm" disabled={isFetchingNextPage} onClick={() => {
              previousHeight.current = scroller.current?.scrollHeight ?? 0;
              void fetchNextPage();
            }}>
              {isFetchingNextPage ? "Loading…" : "Load older messages"}
            </Button>
          </div>
        )}
        {isPending ? (
          <Skeleton className="h-16" aria-hidden="true" />
        ) : messages.length === 0 && waiting.length === 0 ? (
          <p className="py-8 text-center text-sm text-muted-foreground">No messages yet. Say hi!</p>
        ) : (
          <ol aria-label="Messages" className="space-y-3">
            {messages.map((message) => {
              // Authors edit and delete their own, moderators delete anyone's; never in an earlier era (BR-15).
              const current = !message.deleted && message.era === channel.currentEra;
              const mine = !!me?.id && message.author?.id === me.id && !!channel.canWrite;
              return (
                <li key={message.id}>
                  <MessageItem message={message} channelId={channelId} canEdit={current && mine} canDelete={current && (mine || !!channel.canModerate)} />
                </li>
              );
            })}
            {waiting.map((item) => (
              <li key={item.clientNonce} className={cn("text-sm", !item.failed && "opacity-60")}>
                <p className="break-words whitespace-pre-wrap">{item.body}</p>
                {item.failed
                  ? <p className="text-xs text-destructive">Not sent. <Button variant="link" size="sm" className="h-auto p-0" onClick={() => deliver(item)}>Retry</Button></p>
                  : <p className="text-xs text-muted-foreground">Sending…</p>}
              </li>
            ))}
          </ol>
        )}
      </div>
      {channel.canWrite && !viewingHistory && (
        <MessageComposer onSend={(body) => {
          atBottom.current = true;
          deliver({ clientNonce: newNonce(), body, failed: false });
        }} />
      )}
    </div>
  );
}

function MessageItem({ message, channelId, canEdit, canDelete }: { message: ChatMessage; channelId: string; canEdit: boolean; canDelete: boolean }) {
  const author = message.author?.displayName ?? "Unknown user";
  const edit = useEditMessage(channelId);
  const remove = useDeleteMessage(channelId);
  const [draft, setDraft] = useState<string | null>(null);              // null: not editing
  const [deleting, setDeleting] = useState(false);

  function save() {
    edit.mutate({ messageId: message.id ?? 0, body: (draft ?? "").trim() }, {
      onSuccess: () => setDraft(null),
      onError: (error) => toast.error(error.message),
    });
  }

  return (
    <div className="group flex gap-2 text-sm">
      <ClubAvatar name={author} url={message.author?.avatarUrl} size="sm" />
      <div className="min-w-0 flex-1">
        <p>
          <span className="font-medium">{author}</span>{" "}
          {message.createdAt && (
            <time dateTime={message.createdAt} title={formatDateTime(message.createdAt)} className="text-xs text-muted-foreground">
              {formatTime(message.createdAt)}{message.editedAt && " · edited"}
            </time>
          )}
        </p>
        {message.deleted ? (
          <p className="text-muted-foreground italic">Message deleted.</p>
        ) : draft !== null ? (
          <form className="space-y-2" onSubmit={(event) => { event.preventDefault(); save(); }}>
            <Textarea aria-label="Edit your message" rows={2} autoFocus value={draft} onChange={(event) => setDraft(event.target.value)} />
            <div className="flex gap-2">
              <Button type="button" variant="ghost" size="sm" onClick={() => setDraft(null)}>Cancel</Button>
              <Button type="submit" size="sm" disabled={!draft.trim() || edit.isPending}>Save</Button>
            </div>
          </form>
        ) : (
          <p className="break-words whitespace-pre-wrap">{message.body}</p>
        )}
        {draft === null && (canEdit || canDelete) && (
          <p className="flex gap-2">
            {canEdit && <Button variant="link" size="sm" className="h-auto px-0 text-xs text-muted-foreground" onClick={() => setDraft(message.body ?? "")}>Edit</Button>}
            {canDelete && <Button variant="link" size="sm" className="h-auto px-0 text-xs text-muted-foreground" onClick={() => setDeleting(true)}>Delete</Button>}
          </p>
        )}
      </div>
      <ConfirmDialog open={deleting} onClose={() => setDeleting(false)} title="Delete this message?" confirmLabel="Delete message"
                     description="It is replaced by “Message deleted.” for everyone." onConfirm={() => remove.mutateAsync(message.id ?? 0)} />
    </div>
  );
}
