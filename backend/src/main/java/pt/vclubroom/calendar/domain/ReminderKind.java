package pt.vclubroom.calendar.domain;

import java.time.Duration;

/** When reminders go out before an event starts (calendar.md §4); the table allows exactly these two. */
public enum ReminderKind {
    DAY_BEFORE(Duration.ofHours(24)),
    HOUR_BEFORE(Duration.ofHours(1));

    private final Duration before;

    ReminderKind(Duration before) {
        this.before = before;
    }

    public Duration before() { return before; }
}
