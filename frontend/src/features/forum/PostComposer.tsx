import { useState } from "react";
import { toast } from "sonner";
import { MediaPicker } from "@/features/media";
import { Button } from "@/shared/ui/button";
import { Card, CardContent } from "@/shared/ui/card";
import { Textarea } from "@/shared/ui/textarea";
import { useCreatePost } from "./api";

/** Write a post: Markdown text and/or attachments; publishing waits until every upload is done. */
export function PostComposer({ channelId }: { channelId: string }) {
  const create = useCreatePost(channelId);
  const [body, setBody] = useState("");
  const [mediaIds, setMediaIds] = useState<string[]>([]);
  const [uploading, setUploading] = useState(false);
  const [pickerKey, setPickerKey] = useState(0);       // a new key empties the picker after publishing

  const empty = !body.trim() && mediaIds.length === 0;

  function publish() {
    create.mutate({ body, mediaIds }, {
      onSuccess: () => {
        setBody("");
        setMediaIds([]);
        setPickerKey((k) => k + 1);
      },
      onError: (error) => toast.error(error.message),
    });
  }

  return (
    <Card size="sm">
      <CardContent className="space-y-3">
        <Textarea
          aria-label="Write a post"
          placeholder="Share something with the club… (Markdown works)"
          rows={3}
          value={body}
          onChange={(event) => setBody(event.target.value)}
        />
        <div className="flex flex-wrap items-start justify-between gap-3">
          <MediaPicker
            key={pickerKey}
            purpose="POST_ATTACHMENT"
            onChange={(ids, busy) => {
              setMediaIds(ids);
              setUploading(busy);
            }}
          />
          <Button onClick={publish} disabled={empty || uploading || create.isPending}>
            {uploading ? "Uploading…" : "Post"}
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
