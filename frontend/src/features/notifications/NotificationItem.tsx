import { formatDateTime, formatRelative } from "@/shared/lib/dates";
import { cn } from "@/shared/lib/utils";
import type { Notification } from "./api";

/** Title, body and time; unread ones carry a dot (and say so to screen readers). */
export function NotificationItem({ notification }: { notification: Notification }) {
  return (
    <span className="flex w-full items-start gap-2 text-left text-sm">
      <span aria-hidden="true" className={cn("mt-1.5 size-2 shrink-0 rounded-full", notification.read ? "bg-transparent" : "bg-primary")} />
      <span className="min-w-0 flex-1">
        <span className={cn("block", !notification.read && "font-medium")}>
          {!notification.read && <span className="sr-only">Unread: </span>}
          {notification.title}
        </span>
        {notification.body && <span className="block truncate text-muted-foreground">{notification.body}</span>}
        {notification.createdAt && (
          <time dateTime={notification.createdAt} title={formatDateTime(notification.createdAt)} className="block text-xs text-muted-foreground">
            {formatRelative(notification.createdAt)}
          </time>
        )}
      </span>
    </span>
  );
}
