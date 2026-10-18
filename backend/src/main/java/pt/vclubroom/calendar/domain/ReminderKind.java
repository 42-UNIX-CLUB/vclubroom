package pt.vclubroom.calendar.domain;

import java.time.Duration;

/** Which reminder a row stands for; the offsets themselves come from app.calendar.reminder-offsets. */
public enum ReminderKind {
    DAY_BEFORE,
    HOUR_BEFORE;

    public static ReminderKind forOffset(Duration offset) {
        return offset.compareTo(Duration.ofDays(1)) >= 0 ? DAY_BEFORE : HOUR_BEFORE;
    }
}
