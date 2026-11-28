package pt.vclubroom.notification.application;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pt.vclubroom.identity.events.AccessTokenExpiring;
import pt.vclubroom.notification.domain.NotificationType;

/** Personal access tokens (E20-S02): a week before one expires, its owner is told, in the app and by email. */
@Component
class IdentityNotificationListener {

    private final NotificationDispatcher dispatcher;
    private final DateTimeFormatter day;

    /** Dates in the app's time zone, like the calendar's emails. */
    IdentityNotificationListener(NotificationDispatcher dispatcher, @Value("${app.calendar.display-zone:Europe/Lisbon}") ZoneId zone) {
        this.dispatcher = dispatcher;
        this.day = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(zone);
    }

    @ApplicationModuleListener
    void on(AccessTokenExpiring e) {
        dispatcher.dispatch(new NotificationDraft(NotificationType.ACCESS_TOKEN_EXPIRING,
            "Your token “" + e.tokenName() + "” expires on " + day.format(e.expiresAt()),
            "Create a new one in Settings › Developer tokens before then, and put it in the scripts that use this one.",
            "/settings#tokens", Map.of("tokenId", e.tokenId().toString()), "ACCESS_TOKEN_EXPIRING:" + e.tokenId()),
            Set.of(e.userId()));
    }
}
