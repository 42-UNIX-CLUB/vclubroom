import ReactMarkdown, { type Components } from "react-markdown";
import rehypeSanitize from "rehype-sanitize";
import { cn } from "@/shared/lib/utils";

/** Links in user content open in a new tab and pass no referrer or ranking (07-security §7). */
const components: Components = {
  a: ({ href, children }) => (
    <a href={href} target="_blank" rel="noopener noreferrer nofollow" className="text-primary underline underline-offset-4">
      {children}
    </a>
  ),
};

/**
 * Renders user-written Markdown (posts, club descriptions). Raw HTML is never rendered,
 * and rehype-sanitize strips anything unsafe that Markdown itself can express (e.g. javascript: links).
 */
export function Markdown({ children, className }: { children: string; className?: string }) {
  return (
    <div
      className={cn(
        "space-y-3 text-sm leading-relaxed break-words",
        "[&_ul]:list-disc [&_ul]:pl-5 [&_ol]:list-decimal [&_ol]:pl-5 [&_h1]:text-xl [&_h2]:text-lg [&_h1,&_h2,&_h3]:font-semibold",
        "[&_code]:rounded [&_code]:bg-muted [&_code]:px-1 [&_pre]:overflow-x-auto [&_pre]:rounded-lg [&_pre]:bg-muted [&_pre]:p-3",
        "[&_blockquote]:border-l-2 [&_blockquote]:pl-3 [&_blockquote]:text-muted-foreground",
        className,
      )}
    >
      <ReactMarkdown rehypePlugins={[rehypeSanitize]} components={components}>
        {children}
      </ReactMarkdown>
    </div>
  );
}