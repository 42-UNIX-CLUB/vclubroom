package pt.vclubroom.club.spi;

import java.util.UUID;

import pt.vclubroom.shared.security.Actor;

/**
 * Dependency inversion: the club module needs to know whether someone may manage a club's settings,
 * but permissions live in the membership module, which itself depends on club.
 * Membership implements this interface; club never references membership.
 */
public interface ClubManagementGuard {

    boolean canManageSettings(UUID clubId, Actor actor);
}