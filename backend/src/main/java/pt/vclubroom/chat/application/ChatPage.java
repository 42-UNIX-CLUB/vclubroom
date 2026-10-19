package pt.vclubroom.chat.application;

import java.util.List;

/** Oldest first, for display; nextBefore = the cursor of the older page, null when there is none. */
public record ChatPage(List<ChatMessageView> items, Long nextBefore) {
}
