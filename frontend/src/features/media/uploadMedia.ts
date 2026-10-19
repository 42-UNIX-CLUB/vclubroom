import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type MediaPurpose = components["schemas"]["CreateUploadRequest"]["purpose"];

/** 1) ask for a slot  2) PUT the bytes to storage (presigned, through the proxy)  3) confirm. Returns the mediaId. */
export async function uploadMedia(file: File, purpose: MediaPurpose,
                                  onProgress?: (percent: number) => void): Promise<string> {
  const { data: slot } = await api.POST("/api/media/uploads", {
    body: { purpose, fileName: file.name, contentType: file.type, sizeBytes: file.size },
  });
  if (!slot?.mediaId || !slot.uploadUrl) throw new Error("No upload slot");
  await putWithProgress(slot.uploadUrl, file, slot.headers ?? {}, onProgress);
  await api.POST("/api/media/{mediaId}/complete", { params: { path: { mediaId: slot.mediaId } } });
  return slot.mediaId;
}

function putWithProgress(url: string, file: File, signedHeaders: Record<string, string[]>,
                         onProgress?: (percent: number) => void): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();               // fetch() has no upload progress events
    xhr.open("PUT", url);
    Object.entries(signedHeaders).forEach(([name, values]) => {
      if (name.toLowerCase() !== "host") xhr.setRequestHeader(name, values.join(","));   // Host is set by the browser
    });
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress?.(Math.round((e.loaded / e.total) * 100));
    };
    xhr.onload = () => (xhr.status >= 200 && xhr.status < 300 ? resolve() : reject(new Error(`Upload failed (${xhr.status})`)));
    xhr.onerror = () => reject(new Error("Upload failed"));
    xhr.send(file);
  });
}
