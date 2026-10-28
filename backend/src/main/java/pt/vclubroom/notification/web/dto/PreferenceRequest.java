package pt.vclubroom.notification.web.dto;

import jakarta.validation.constraints.NotNull;
import pt.vclubroom.notification.application.PreferenceService;
import pt.vclubroom.notification.domain.NotificationType;

public record PreferenceRequest(@NotNull NotificationType type, boolean inApp, boolean email) {

    public PreferenceService.Choice toChoice() {
        return new PreferenceService.Choice(type, inApp, email);
    }
}
