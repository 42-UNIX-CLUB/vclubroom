import { BellIcon } from "lucide-react";
import { useNavigate } from "react-router";
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger,
} from "@/shared/ui/dropdown-menu";
import { type Notification, useMarkAllRead, useMarkRead, useNotifications, useNotificationsLive, useUnreadCount } from "./api";
import { NotificationItem } from "./NotificationItem";

/**
 * The bell in the top bar (08-frontend §9.3): the unread count, live, and the five newest notifications. A click
 * marks one read and opens its target; "See all" opens the inbox.
 */
export function NotificationBell() {
  const navigate = useNavigate();
  const { data: unread = 0 } = useUnreadCount();
  const { data } = useNotifications(false);
  const markRead = useMarkRead();
  const markAllRead = useMarkAllRead();
  useNotificationsLive();
  const latest = data?.pages[0]?.items?.slice(0, 5) ?? [];

  function open(notification: Notification) {
    if (!notification.read && notification.id) markRead.mutate(notification.id);
    if (notification.link) void navigate(notification.link);
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={unread > 0 ? `Notifications, ${unread} unread` : "Notifications"}
        className="relative inline-flex size-8 items-center justify-center rounded-lg outline-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50"
      >
        <BellIcon className="size-4" />
        {unread > 0 && (
          <span aria-hidden="true" className="absolute -top-0.5 -right-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-primary px-1 text-[10px] font-semibold text-primary-foreground">
            {unread > 9 ? "9+" : unread}
          </span>
        )}
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-80">
        <DropdownMenuGroup>
          <DropdownMenuLabel className="flex items-center justify-between">
            Notifications
            {unread > 0 && (
              <button type="button" className="text-xs font-normal text-primary hover:underline" onClick={() => markAllRead.mutate()}>
                Mark all as read
              </button>
            )}
          </DropdownMenuLabel>
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        {latest.length === 0
          ? <p className="px-2 py-4 text-center text-sm text-muted-foreground">Nothing yet.</p>
          : latest.map((notification) => (
            <DropdownMenuItem key={notification.id} onClick={() => open(notification)}>
              <NotificationItem notification={notification} />
            </DropdownMenuItem>
          ))}
        <DropdownMenuSeparator />
        <DropdownMenuItem onClick={() => void navigate("/notifications")} className="justify-center text-primary">See all</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
