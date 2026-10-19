import { useQuery } from "@tanstack/react-query";
import { LockIcon, SearchXIcon } from "lucide-react";
import { Navigate, useParams } from "react-router";
import { api } from "@/shared/api/client";
import { ApiError } from "@/shared/api/errors";
import { EmptyState } from "@/shared/ui/EmptyState";
import { FullPageSpinner } from "@/shared/ui/FullPageSpinner";

/**
 * /posts/:postId, the link in notifications: finds the post's channel and club, then goes to the channel.
 * Three small requests, because the post knows its channel, the channel its space, and the club its slug.
 */
export function PostRedirectPage() {
  const { postId = "" } = useParams();
  const { data: path, error } = useQuery({
    queryKey: ["post", postId, "location"],
    queryFn: async () => {
      const post = (await api.GET("/api/posts/{postId}", { params: { path: { postId } } })).data;
      const channel = (await api.GET("/api/channels/{channelId}", { params: { path: { channelId: post?.channelId ?? "" } } })).data;
      const club = (await api.GET("/api/clubs/{idOrSlug}", { params: { path: { idOrSlug: post?.clubId ?? "" } } })).data;
      return `/c/${club?.slug}/s/${channel?.spaceSlug}/${channel?.slug}`;
    },
    retry: false,
  });

  if (error) {
    return error instanceof ApiError && error.status === 403
      ? <EmptyState icon={LockIcon} title="You cannot see this post" description="It is in a space you cannot read." />
      : <EmptyState icon={SearchXIcon} title="Post not found" />;
  }
  return path ? <Navigate to={path} replace /> : <FullPageSpinner />;
}
