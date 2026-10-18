package pt.vclubroom.club.application;

import java.util.UUID;

import org.springframework.stereotype.Component;

import pt.vclubroom.club.spi.ClubManagementGuard;
import pt.vclubroom.shared.security.Actor;

/**
 * TEMPORARY (E04-S01-T5): only admins may manage club settings until the membership module implements
 * ClubManagementGuard with the CLUB_SETTINGS_MANAGE permission. E07-S01-T4 deletes this class; two
 * implementations would make Spring fail at start-up.
 */
@Component
class AdminOnlyClubManagementGuard implements ClubManagementGuard {

    @Override
    public boolean canManageSettings(UUID clubId, Actor actor) {
        return actor.admin();
    }
}