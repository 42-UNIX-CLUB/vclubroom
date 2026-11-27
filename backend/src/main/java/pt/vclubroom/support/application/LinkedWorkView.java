package pt.vclubroom.support.application;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import pt.vclubroom.support.domain.LinkedWork;
import pt.vclubroom.support.domain.WorkState;

/** Linked work as the reporter sees it: "E09-S04", "Edit, delete, pin and moderate", in progress, for v1.2.0. */
public record LinkedWorkView(String ref, String title, WorkState state, String milestone, boolean released) {

    private static final Pattern BACKLOG_TITLE = Pattern.compile("^(E\\d{2}(?:-S\\d{2}(?:-T\\d+)?)?) · (.+)$");

    static LinkedWorkView of(LinkedWork w) {
        Matcher m = BACKLOG_TITLE.matcher(w.title());
        return m.matches()
            ? new LinkedWorkView(m.group(1), m.group(2), w.state(), w.milestone(), w.released())
            : new LinkedWorkView("#" + w.issue(), w.title(), w.state(), w.milestone(), w.released());
    }
}
