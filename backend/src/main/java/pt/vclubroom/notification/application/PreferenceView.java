package pt.vclubroom.notification.application;

import pt.vclubroom.notification.domain.NotificationType;

/** One line of the preferences form: what applies now, and the defaults it differs from (or not). */
public record PreferenceView(NotificationType type, boolean inApp, boolean email, boolean defaultInApp, boolean defaultEmail) {
}
