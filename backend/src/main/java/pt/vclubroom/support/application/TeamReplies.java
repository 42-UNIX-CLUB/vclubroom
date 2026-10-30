package pt.vclubroom.support.application;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The team's rule in the tracker: a comment reaches the reporter only if it starts with /reply. Every other comment
 * stays between the team, so forgetting the prefix keeps a note private instead of publishing it.
 */
final class TeamReplies {

    private static final Pattern REPLY = Pattern.compile("^\\s*/reply\\b[ \\t]*\\R?", Pattern.CASE_INSENSITIVE);

    private TeamReplies() {
    }

    /** "/reply Thanks, fixed!" → "Thanks, fixed!"; anything else → empty. */
    static Optional<String> forReporter(String comment) {
        Matcher m = REPLY.matcher(comment == null ? "" : comment);
        if (!m.find()) {
            return Optional.empty();
        }
        String text = comment.substring(m.end()).strip();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }
}
