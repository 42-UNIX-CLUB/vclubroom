package pt.vclubroom.club.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.club.domain.Club;
import pt.vclubroom.club.domain.Slug;
import pt.vclubroom.club.events.ClubApproved;
import pt.vclubroom.club.events.ClubArchived;
import pt.vclubroom.club.events.ClubCreationRequested;
import pt.vclubroom.club.events.ClubProfileUpdated;
import pt.vclubroom.club.events.ClubRejected;
import pt.vclubroom.club.persistence.ClubRepository;
import pt.vclubroom.club.spi.ClubManagementGuard;
import pt.vclubroom.media.api.MediaCatalog;
import pt.vclubroom.media.api.MediaPurpose;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.ForbiddenException;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;

/** Club use cases that change state. */
@Service
public class ClubService {

    private final ClubRepository clubs;
    private final ClubManagementGuard managementGuard;
    private final MediaCatalog mediaCatalog;
    private final ClubDetailsAssembler assembler;
    private final ClubProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ClubService(ClubRepository clubs, ClubManagementGuard managementGuard, MediaCatalog mediaCatalog,
                       ClubDetailsAssembler assembler, ClubProperties properties, ApplicationEventPublisher events,
                       Clock clock) {
        this.clubs = clubs;
        this.managementGuard = managementGuard;
        this.mediaCatalog = mediaCatalog;
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

    /** PENDING_APPROVAL → ACTIVE, era 1. Membership, space, discovery and history react to the events (club.md §8.2). */
    @Transactional
    public ClubDetails approve(Actor actor, UUID clubId) {
        requireAdmin(actor);
        Club club = load(clubId);
        Instant now = clock.instant();
        club.approve(actor.userId(), now);
        events.publishEvent(new ClubApproved(club.id(), club.slug(), club.name(), club.founderId(),
            actor.userId(), club.currentEra(), now));
        events.publishEvent(profileUpdated(club, now));
        return assembler.toDetails(club);
    }

    /** PENDING_APPROVAL → REJECTED (final). The reason is shown to the founder in "My requests". */
    @Transactional
    public ClubDetails reject(Actor actor, UUID clubId, String reason) {
        requireAdmin(actor);
        Club club = load(clubId);
        Instant now = clock.instant();
        club.reject(actor.userId(), reason, now);
        events.publishEvent(new ClubRejected(club.id(), club.name(), club.founderId(), actor.userId(), reason, now));
        return assembler.toDetails(club);
    }

    /**
     * ACTIVE → ARCHIVED (D-13): the current era ends. From now on every write is refused, because the other modules
     * accept writes only in ACTIVE clubs (BR-02), and everything stays readable. The reason is public.
     */
    @Transactional
    public ClubDetails archive(Actor actor, UUID clubId, String reason) {
        requireAdmin(actor);
        Club club = load(clubId);
        Instant now = clock.instant();
        int endedEra = club.currentEra();
        club.archive(actor.userId(), reason, now);                         // CLUB_NOT_ACTIVE unless ACTIVE
        events.publishEvent(new ClubArchived(club.id(), club.name(), endedEra, actor.userId(), reason, now));
        events.publishEvent(profileUpdated(club, now));                    // discovery: the "Archived" badge
        return assembler.toDetails(club);
    }

    /**
     * A partial update (club.md §8.5). A pending club is edited by its founder (or an admin); an active one by whoever
     * holds CLUB_SETTINGS_MANAGE, which membership answers through the SPI. New images must be the actor's own
     * uploads for the right purpose (BR-19). Switching to OPEN lets everyone waiting in (membership listens).
     */
    @Transactional
    public ClubDetails updateProfile(Actor actor, UUID clubId, UpdateClubCommand cmd) {
        Club club = load(clubId);
        boolean allowed = switch (club.status()) {
            case PENDING_APPROVAL -> actor.admin() || club.founderId().equals(actor.userId());
            case ACTIVE -> managementGuard.canManageSettings(clubId, actor);
            default -> false;
        };
        if (!allowed) {
            throw new ForbiddenException("PERMISSION_DENIED", "You cannot edit this club.");
        }
        properties.requireValidCategory(cmd.category());

        if (cmd.avatarMediaId() != null && !cmd.avatarMediaId().equals(club.avatarMediaId())) {
            mediaCatalog.attach(List.of(cmd.avatarMediaId()), actor.userId(), MediaPurpose.CLUB_AVATAR);
        }
        if (cmd.bannerMediaId() != null && !cmd.bannerMediaId().equals(club.bannerMediaId())) {
            mediaCatalog.attach(List.of(cmd.bannerMediaId()), actor.userId(), MediaPurpose.CLUB_BANNER);
        }

        Instant now = clock.instant();
        club.updateProfile(cmd.name(), cmd.tagline(), cmd.description(), cmd.category(), cmd.tags(),
            cmd.avatarMediaId(), cmd.bannerMediaId(), now);
        if (cmd.joinPolicy() != null && cmd.joinPolicy() != club.joinPolicy()) {
            club.changeJoinPolicy(cmd.joinPolicy(), now);                  // CLUB_NOT_ACTIVE unless ACTIVE
        }
        events.publishEvent(profileUpdated(club, now));                    // discovery; membership on OPEN
        return assembler.toDetails(club);
    }

    private ClubProfileUpdated profileUpdated(Club club, Instant now) {
        return new ClubProfileUpdated(club.id(), club.slug(), club.name(), club.tagline(), club.description(),
            club.category(), club.tags(), club.avatarMediaId(), club.bannerMediaId(), club.joinPolicy(),
            club.status(), club.currentEra(), now);
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

    private Club load(UUID clubId) {
        return clubs.findWithErasById(clubId)
            .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Club not found"));
    }

    /** /api/admin/** already requires ROLE_ADMIN; the service checks again so no other caller can skip it. */
    private static void requireAdmin(Actor actor) {
        if (!actor.admin()) {
            throw new ForbiddenException("ADMIN_ONLY", "Only admins can do this.");
        }
    }
}
