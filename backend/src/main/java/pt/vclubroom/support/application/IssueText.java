package pt.vclubroom.support.application;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.support.domain.Ticket;

/**
 * The issue a ticket becomes, and the comments reporters' replies become. What people wrote goes in a fenced block:
 * the tracker shows it as typed, so their text can't add links, images or mentions to the team's page.
 */
final class IssueText {

    private static final Pattern TILDES = Pattern.compile("~{3,}");

    /** How the team talks to the reporter (E17-S03), at the end of every issue. */
    static final String TEAM_NOTE = """

        ---
        To answer in the app, start a comment with `/reply`: other comments stay between the team. The reporter sees \
        the status: add `status/in-progress` or `status/waiting-on-reporter`, or close the issue (with \
        `resolution/wont-fix` or `resolution/duplicate` first to say why).
        """;

    private IssueText() {
    }

    static String title(Ticket t) {
        return t.ref() + " " + t.title();
    }

    static String body(Ticket t, UserSummary reporter, String publicUrl) {
        StringBuilder body = new StringBuilder();
        body.append("**").append(t.ref()).append("** · ").append(label(t)).append(" · sent from the app by ")
            .append(reporter == null ? "a deleted account" : "**%s** (`%s`)".formatted(reporter.displayName(), reporter.login()))
            .append("\n\n");
        if (t.pageUrl() != null) body.append("Page: ").append(publicUrl).append(t.pageUrl()).append("\n");
        if (t.userAgent() != null) body.append("Browser: `").append(t.userAgent().replace("`", "'")).append("`\n");
        body.append("Reporter ID: `").append(t.reporterId()).append("`\n\n");
        return body.append(verbatim(t.description())).append(TEAM_NOTE).toString();
    }

    static String reply(UserSummary reporter, String text) {
        return (reporter == null ? "The reporter" : "**" + reporter.displayName() + "**") + " replied in the app:\n\n" + verbatim(text);
    }

    /** A fence longer than any run of tildes in the text, so nothing in it can close the block. */
    static String verbatim(String text) {
        int longest = 2;
        Matcher m = TILDES.matcher(text);
        while (m.find()) longest = Math.max(longest, m.group().length());
        String fence = "~".repeat(longest + 1);
        return fence + "text\n" + text + "\n" + fence + "\n";
    }

    private static String label(Ticket t) {
        return switch (t.type()) {
            case BUG -> "Bug";
            case SUGGESTION -> "Suggestion";
            case QUESTION -> "Question";
            case ACCOUNT -> "Club or account problem";
            case REPORT -> "Report";
            case ACCESSIBILITY -> "Accessibility";
            case PRIVACY -> "Privacy or data request";
            case OTHER -> "Other";
        };
    }
}
