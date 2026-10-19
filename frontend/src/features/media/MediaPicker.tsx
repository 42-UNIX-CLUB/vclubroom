import { FileAudioIcon, FileVideoIcon, PaperclipIcon, XIcon } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Button } from "@/shared/ui/button";
import { type MediaPurpose, uploadMedia } from "./uploadMedia";

/** Client-side copy of the backend's limits (app.media.limits, R-12), to refuse early; the backend checks again. */
const MAX_BYTES = { image: 10 * 1024 * 1024, audio: 50 * 1024 * 1024, video: 90 * 1024 * 1024 };
const IMAGE_ONLY: MediaPurpose[] = ["CLUB_AVATAR", "CLUB_BANNER", "EVENT_COVER"];

interface Item {
  key: string;
  file: File;
  progress: number;
  status: "uploading" | "ready" | "error";
  mediaId?: string;
  error?: string;
  previewUrl?: string;
}

function kindOf(file: File): keyof typeof MAX_BYTES | null {
  const prefix = file.type.split("/")[0];
  return prefix === "image" || prefix === "audio" || prefix === "video" ? prefix : null;
}

function problemWith(file: File, purpose: MediaPurpose): string | null {
  const kind = kindOf(file);
  if (!kind) return "Only images, videos and audio files can be attached.";
  if (IMAGE_ONLY.includes(purpose) && kind !== "image") return "Only images are allowed here.";
  if (file.size > MAX_BYTES[kind]) return `Too large: ${kind}s can be up to ${Math.round(MAX_BYTES[kind] / 1024 / 1024)} MB.`;
  return null;
}

const megabytes = (bytes: number) => `${(bytes / 1024 / 1024).toFixed(bytes < 1024 * 1024 ? 2 : 1)} MB`;

/**
 * Picks files and uploads each one straight to storage with its own progress bar (08-frontend §11.2).
 * onChange receives the IDs of the files that are ready, and whether uploads are still running
 * (a form should wait for them before submitting).
 */
export function MediaPicker({ purpose, onChange, max = 4, accept = "image/*,video/*,audio/*" }: {
  purpose: MediaPurpose;
  onChange: (mediaIds: string[], uploading: boolean) => void;
  max?: number;
  accept?: string;
}) {
  const [items, setItems] = useState<Item[]>([]);
  const input = useRef<HTMLInputElement>(null);
  const nextKey = useRef(0);          // not crypto.randomUUID(): it only exists on https or localhost
  const onChangeRef = useRef(onChange);
  useEffect(() => {
    onChangeRef.current = onChange;
  });

  // Tell the parent only when the set of ready files, or "still uploading", actually changes.
  const readyKey = items.filter((i) => i.status === "ready").map((i) => i.mediaId).join(",");
  const uploading = items.some((i) => i.status === "uploading");
  useEffect(() => {
    onChangeRef.current(readyKey ? readyKey.split(",") : [], uploading);
  }, [readyKey, uploading]);

  // Free the image previews when the picker goes away.
  const previews = useRef<string[]>([]);
  useEffect(() => () => previews.current.forEach((url) => URL.revokeObjectURL(url)), []);

  function patch(key: string, change: Partial<Item>) {
    setItems((current) => current.map((item) => (item.key === key ? { ...item, ...change } : item)));
  }

  function addFiles(files: FileList | null) {
    const added = Array.from(files ?? []).slice(0, Math.max(max - items.length, 0)).map((file): Item => {
      const problem = problemWith(file, purpose);
      const previewUrl = !problem && kindOf(file) === "image" && typeof URL.createObjectURL === "function"
        ? URL.createObjectURL(file) : undefined;
      if (previewUrl) previews.current.push(previewUrl);
      return { key: String(nextKey.current++), file, progress: 0, status: problem ? "error" : "uploading", error: problem ?? undefined, previewUrl };
    });
    setItems((current) => [...current, ...added]);
    added.filter((item) => item.status === "uploading").forEach((item) => {
      uploadMedia(item.file, purpose, (progress) => patch(item.key, { progress }))
        .then((mediaId) => patch(item.key, { status: "ready", progress: 100, mediaId }))
        .catch((error: Error) => patch(item.key, { status: "error", error: error.message }));
    });
  }

  function remove(key: string) {
    setItems((current) => current.filter((item) => item.key !== key));
  }

  return (
    <div className="space-y-2">
      <input
        ref={input}
        type="file"
        multiple
        accept={accept}
        aria-label="Choose files"
        className="sr-only"
        onChange={(event) => {
          addFiles(event.target.files);
          event.target.value = "";                    // choosing the same file again fires onChange again
        }}
      />
      <Button type="button" variant="outline" size="sm" disabled={items.length >= max} onClick={() => input.current?.click()}>
        <PaperclipIcon /> {IMAGE_ONLY.includes(purpose) ? "Choose an image" : "Add photos, videos or audio"}
      </Button>
      {items.length > 0 && (
        <ul className="space-y-2">
          {items.map((item) => (
            <li key={item.key} className="flex items-center gap-3 rounded-lg border p-2">
              <Thumbnail item={item} />
              <div className="min-w-0 flex-1 space-y-1">
                <p className="truncate text-sm">
                  {item.file.name} <span className="text-muted-foreground">· {megabytes(item.file.size)}</span>
                </p>
                {item.status === "uploading" && (
                  <div
                    role="progressbar"
                    aria-label={`Uploading ${item.file.name}`}
                    aria-valuemin={0}
                    aria-valuemax={100}
                    aria-valuenow={item.progress}
                    className="h-1.5 overflow-hidden rounded-full bg-muted"
                  >
                    <div className="h-full rounded-full bg-primary transition-[width]" style={{ width: `${item.progress}%` }} />
                  </div>
                )}
                {item.status === "ready" && <p className="text-xs text-muted-foreground">Uploaded</p>}
                {item.status === "error" && <p role="alert" className="text-xs text-destructive">{item.error}</p>}
              </div>
              <Button type="button" variant="ghost" size="icon" aria-label={`Remove ${item.file.name}`} onClick={() => remove(item.key)}>
                <XIcon />
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function Thumbnail({ item }: { item: Item }) {
  const kind = kindOf(item.file);
  if (item.previewUrl) return <img src={item.previewUrl} alt="" className="size-10 rounded object-cover" />;
  const Icon = kind === "audio" ? FileAudioIcon : FileVideoIcon;
  return (
    <span className="flex size-10 items-center justify-center rounded bg-muted">
      <Icon className="size-5 text-muted-foreground" aria-hidden="true" />
    </span>
  );
}
