package pt.vclubroom.club.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.club.domain.Club;
import pt.vclubroom.club.domain.Slug;
import pt.vclubroom.club.events.ClubCreationRequested;
import pt.vclubroom.club.persistence.ClubRepository;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.security.Actor;

/**
 * Club use cases that change state. E04-S01 needs only requestCreation; E04-S02 adds approve/reject,
 * E04-S04 updateProfile (through ClubManagementGuard and media), E14-S01 archive (club.md §9.3).
 */
@Service
public class ClubService {

    private final ClubRepository clubs;
    private final ClubDetailsAssembler assembler;
    private final ClubProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ClubService(ClubRepository clubs, ClubDetailsAssembler assembler, ClubProperties properties,
                       ApplicationEventPublisher events, Clock clock) {
        this.clubs = clubs;
        this.assembler = assembler;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public ClubDetails requestCreation(Actor actor, CreateClubCommand cmd) {
        properties.requireValidCategory(cmd.category());
        long pending = clubs.countByFounderIdAndStatus(actor.userId(), ClubStatus.PENDING_APPROVAL);
        if (pending >= properties.maxPendingRequestsPerUser()) {
            throw new BusinessRuleException("TOO_MANY_PENDING_REQUESTS",
                "You already have " + pending + " club requests waiting for review.");
        }
        Instant now = clock.instant();
        Club club = Club.requestCreation(uniqueSlug(Slug.fromName(cmd.name())), cmd.name(), cmd.tagline(),
            cmd.description(), cmd.category(), cmd.tags(), actor.userId(), now);
        clubs.save(club);
        events.publishEvent(new ClubCreationRequested(club.id(), club.slug(), club.name(), actor.userId(), now));
        return assembler.toDetails(club);
    }

    /** "chess-club", then "chess-club-2", "chess-club-3"… (BR-01: slugs are unique). */
    private Slug uniqueSlug(Slug base) {
        Slug candidate = base;
        int n = 2;
        while (clubs.existsBySlug(candidate.value())) {
            candidate = base.withSuffix(n++);
        }
        return candidate;
    }
}