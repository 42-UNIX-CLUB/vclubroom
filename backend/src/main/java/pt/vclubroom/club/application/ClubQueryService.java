package pt.vclubroom.club.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.club.domain.Club;
import pt.vclubroom.club.persistence.ClubRepository;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.shared.web.PageResponse;

/** Read side of the club module. */
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

    /** Admin lists, oldest first (the approval queue is first come, first served). */
    public PageResponse<ClubDetails> byStatus(ClubStatus status, int page, int size) {
        Page<Club> result = clubs.findByStatus(status,
            PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.ASC, "createdAt")));
        return new PageResponse<>(result.map(assembler::toDetails).getContent(), page, size, result.getTotalElements());
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
