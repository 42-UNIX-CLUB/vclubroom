package pt.vclubroom.notification.web;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.notification.application.PreferenceService;
import pt.vclubroom.notification.application.PreferenceView;
import pt.vclubroom.notification.web.dto.PreferenceRequest;
import pt.vclubroom.shared.security.AuthenticatedUser;

/** My notification preferences (notification.md §6): every type, in-app and email. */
@RestController
@RequestMapping("/api/me/notification-preferences")
class PreferenceController {

    private final PreferenceService preferences;

    PreferenceController(PreferenceService preferences) {
        this.preferences = preferences;
    }

    @GetMapping
    List<PreferenceView> mine(@AuthenticationPrincipal AuthenticatedUser me) {
        return preferences.allFor(me.actor().userId());
    }

    /** Sends some or all types; returns the whole list as it now applies. */
    @PutMapping
    List<PreferenceView> save(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody List<@Valid PreferenceRequest> request) {
        return preferences.save(me.actor().userId(), request.stream().map(PreferenceRequest::toChoice).toList());
    }
}
