import { SendHorizontalIcon } from "lucide-react";
import { useState } from "react";
import { Button } from "@/shared/ui/button";
import { Textarea } from "@/shared/ui/textarea";

/** Enter sends, Shift+Enter starts a new line. */
export function MessageComposer({ onSend }: { onSend: (body: string) => void }) {
  const [body, setBody] = useState("");

  function send() {
    const text = body.trim();
    if (!text) return;
    onSend(text);
    setBody("");
  }

  return (
    <form className="flex items-end gap-2" onSubmit={(event) => { event.preventDefault(); send(); }}>
      <Textarea
        aria-label="Write a message"
        placeholder="Write a message…"
        rows={1}
        className="min-h-10 resize-none"
        value={body}
        onChange={(event) => setBody(event.target.value)}
        onKeyDown={(event) => {
          // Not while an input method is composing: there Enter confirms a character (Safari reports keyCode 229).
          if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing && event.keyCode !== 229) {
            event.preventDefault();
            send();
          }
        }}
      />
      <Button type="submit" size="icon" aria-label="Send" disabled={!body.trim()}><SendHorizontalIcon /></Button>
    </form>
  );
}
