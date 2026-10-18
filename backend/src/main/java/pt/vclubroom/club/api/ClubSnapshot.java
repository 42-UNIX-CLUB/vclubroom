package pt.vclubroom.club.api;

import java.util.UUID;

/** Immutable, cross-module view of a club. */
public record ClubSnapshot(UUID id, String slug, String name, ClubStatus status, JoinPolicy joinPolicy, int currentEra) {

    public boolean isActive() {
        return status == ClubStatus.ACTIVE;
    }
}