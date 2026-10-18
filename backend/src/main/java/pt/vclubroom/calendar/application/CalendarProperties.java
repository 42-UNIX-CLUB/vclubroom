package pt.vclubroom.calendar.application;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** app.calendar.* in application.yml: how long before an event reminders fire, and the widest calendar query. */
@ConfigurationProperties("app.calendar")
public record CalendarProperties(@DefaultValue({"24h", "1h"}) List<Duration> reminderOffsets,
                                 @DefaultValue("62") int maxRangeDays) {
}
