import { BellOffIcon } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router";
import { Button } from "@/shared/ui/button";
import { EmptyState } from "@/shared/ui/EmptyState";
import { PageHeader } from "@/shared/ui/PageHeader";
import { Skeleton } from "@/shared/ui/skeleton";
import { Tabs, TabsList, TabsTrigger } from "@/shared/ui/tabs";
import { type Notification, useMarkAllRead, useMarkRead, useNotifications, useUnreadCount } from "./api";
import { NotificationItem } from "./NotificationItem";

/** /notifications: the whole inbox, newest first; All or Unread; a click opens the target and marks it read. */
export function NotificationsPage() {
  const navigate = useNavigate();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const { data, isPending, hasNextPage, fetchNextPage, isFetchingNextPage } = useNotifications(unreadOnly);
  const { data: unread = 0 } = useUnreadCount();
  const markRead = useMarkRead();
  const markAllRead = useMarkAllRead();
  const notifications = data?.pages.flatMap((page) => page.items ?? []) ?? [];

  function open(notification: Notification) {
    if (!notification.read && notification.id) markRead.mutate(notification.id);
    if (notification.link) void navigate(notification.link);
  }

  return (
    <>
      <PageHeader title="Notifications" />
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        <Tabs value={unreadOnly ? "unread" : "all"} onValueChange={(value) => setUnreadOnly(value === "unread")}>
          <TabsList>
            <TabsTrigger value="all">All</TabsTrigger>
            <TabsTrigger value="unread">Unread{unread > 0 && ` (${unread})`}</TabsTrigger>
          </TabsList>
        </Tabs>
        <Button variant="outline" size="sm" disabled={unread === 0 || markAllRead.isPending} onClick={() => markAllRead.mutate()}>
          Mark all as read
        </Button>
      </div>
      {isPending ? (
        <Skeleton className="h-40" aria-hidden="true" />
      ) : notifications.length === 0 ? (
        <EmptyState icon={BellOffIcon} title={unreadOnly ? "All caught up" : "No notifications yet"}
          description="Club decisions, new posts and comments, and events show up here." />
      ) : (
        <ul className="divide-y rounded-xl border">
          {notifications.map((notification) => (
            <li key={notification.id}>
              <button type="button" onClick={() => open(notification)}
                className="w-full p-3 outline-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50">
                <NotificationItem notification={notification} />
              </button>
            </li>
          ))}
        </ul>
      )}
      {hasNextPage && (
        <div className="mt-4 flex justify-center">
          <Button variant="outline" disabled={isFetchingNextPage} onClick={() => void fetchNextPage()}>
            {isFetchingNextPage ? "Loading…" : "Older notifications"}
          </Button>
        </div>
      )}
    </>
  );
}
