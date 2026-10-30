import {
  AccessibilityIcon, BugIcon, CircleQuestionMarkIcon, FlagIcon, LightbulbIcon, LockIcon, type LucideIcon, MessageSquareIcon,
  UserCogIcon,
} from "lucide-react";
import type { TicketType } from "./api";

/** The form's choices, in this order. The browser goes with the types that set `withBrowser`. */
export const TICKET_TYPES: { type: TicketType; label: string; description: string; icon: LucideIcon; withBrowser?: boolean }[] = [
  { type: "BUG", label: "Something doesn't work", description: "A page, button or feature behaves wrongly or shows an error.", icon: BugIcon, withBrowser: true },
  { type: "SUGGESTION", label: "Suggestion", description: "An idea or an improvement for Virtual Clubroom.", icon: LightbulbIcon },
  { type: "QUESTION", label: "Question", description: "You're not sure how something works.", icon: CircleQuestionMarkIcon },
  { type: "ACCOUNT", label: "Club or account problem", description: "Joining a club, ownership, a club to restore, logging in.", icon: UserCogIcon },
  { type: "REPORT", label: "Report content or behaviour", description: "A post, comment, club or person that breaks the rules.", icon: FlagIcon },
  { type: "ACCESSIBILITY", label: "Accessibility", description: "Hard or impossible to use, for example with a screen reader or only a keyboard.", icon: AccessibilityIcon, withBrowser: true },
  { type: "PRIVACY", label: "Privacy or my data", description: "A copy of your data, a correction, or deleting your account.", icon: LockIcon },
  { type: "OTHER", label: "Something else", description: "Anything that doesn't fit above.", icon: MessageSquareIcon },
];

export function typeLabel(type: TicketType | undefined): string {
  return TICKET_TYPES.find((t) => t.type === type)?.label ?? "Ticket";
}
