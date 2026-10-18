package pt.vclubroom.club.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.club.domain.Club;
import pt.vclubroom.club.persistence.ClubRepository;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;

/** Read side of the club module. E04-S02 adds byStatus for the admin approval queue. */
@Service
@Transactional(readOnly = true)
public class ClubQueryService {

    private final ClubRepository clubs;
    private final ClubDetailsAssembler assembler;

    public ClubQueryService(ClubRepository clubs, ClubDetailsAssembler assembler) {
        this.clubs = clubs;
        this.assembler = assembler;
    }

    /** By UUID or slug. A club the actor may not see answers exactly like a missing one (BR-03: hide, don't reveal). */
    public ClubDetails get(Actor actor, String idOrSlug) {
        Club club = parseUuid(idOrSlug)
            .map(clubs::findWithErasById)
            .orElseGet(() -> clubs.findWithErasBySlug(idOrSlug))
            .filter(c -> c.isVisibleTo(actor.userId(), actor.admin()))
            .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Club not found"));
        return assembler.toDetails(club);
    }

    /** The actor's own requests that are still waiting or were rejected (with the reason), newest first. */
    public List<ClubDetails> myRequests(Actor actor) {
        return clubs.findByFounderIdAndStatusInOrderByCreatedAtDesc(actor.userId(),
                List.of(ClubStatus.PENDING_APPROVAL, ClubStatus.REJECTED))
            .stream().map(assembler::toDetails).toList();
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}