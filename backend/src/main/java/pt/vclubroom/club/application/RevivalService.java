package pt.vclubroom.club.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.club.domain.Club;
import pt.vclubroom.club.domain.RevivalRequest;
import pt.vclubroom.club.domain.RevivalStatus;
import pt.vclubroom.club.events.ClubProfileUpdated;
import pt.vclubroom.club.events.ClubRevivalRequested;
import pt.vclubroom.club.events.ClubRevived;
import pt.vclubroom.club.persistence.ClubRepository;
import pt.vclubroom.club.persistence.RevivalRequestRepository;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.ConflictException;
import pt.vclubroom.shared.error.ForbiddenException;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.shared.web.PageResponse;

/**
 * Reviving an archived club (D-14, club.md §8.4): any student asks, with a motivation; an admin approves one request.
 * The club becomes ACTIVE in a new era, the requester its owner (membership, R-05), and the others waiting are
 * turned down (BR-20).
 */
@Service
public class RevivalService {

    private final RevivalRequestRepository requests;
    private final ClubRepository clubs;
    private final ClubDetailsAssembler assembler;
    private final UserDirectory users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RevivalService(RevivalRequestRepository requests, ClubRepository clubs, ClubDetailsAssembler assembler,
                          UserDirectory users, ApplicationEventPublisher events, Clock clock) {
        this.requests = requests;
        this.clubs = clubs;
        this.assembler = assembler;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    /** One pending request per student and club (ux_revival_pending_per_requester backs the check). */
    @Transactional
    public RevivalRequestView request(Actor actor, UUID clubId, String motivation) {
        Club club = clubs.findById(clubId)
            .filter(c -> c.isVisibleTo(actor.userId(), actor.admin()))
            .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Club not found"));
        if (club.status() != ClubStatus.ARCHIVED) {
            throw new BusinessRuleException("CLUB_NOT_ARCHIVED", "Only archived clubs can be revived.");
        }
        if (requests.existsByClubIdAndRequesterIdAndStatus(clubId, actor.userId(), RevivalStatus.PENDING)) {
            throw new ConflictException("REVIVAL_ALREADY_REQUESTED", "You already asked to revive this club.");
        }
        Instant now = clock.instant();
        RevivalRequest request = requests.save(RevivalRequest.submit(clubId, actor.userId(), motivation.trim(), now));
        events.publishEvent(new ClubRevivalRequested(request.id(), clubId, club.name(), actor.userId(), now));
        return views(List.of(request)).getFirst();
    }

    /** ARCHIVED → ACTIVE, era n+1. A second approval for the same club fails: it is no longer archived. */
    @Transactional
    public ClubDetails approve(Actor actor, UUID requestId) {
        requireAdmin(actor);
        RevivalRequest request = load(requestId);
        Club club = clubs.findWithErasById(request.clubId())
            .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Club not found"));
        Instant now = clock.instant();

        request.approve(actor.userId(), now);
        club.revive(now);                                                  // CLUB_NOT_ARCHIVED unless ARCHIVED
        requests.findByClubIdAndStatus(club.id(), RevivalStatus.PENDING).stream()       // BR-20
            .filter(other -> !other.id().equals(requestId))
            .forEach(other -> other.reject(actor.userId(), "Another revival request was approved.", now));

        events.publishEvent(new ClubRevived(club.id(), club.name(), club.currentEra(), request.requesterId(),
            actor.userId(), request.id(), now));
        events.publishEvent(new ClubProfileUpdated(club.id(), club.slug(), club.name(), club.tagline(),
            club.description(), club.category(), club.tags(), club.avatarMediaId(), club.bannerMediaId(),
            club.joinPolicy(), club.status(), club.currentEra(), now));        // discovery: active again
        return assembler.toDetails(club);
    }

    @Transactional
    public RevivalRequestView reject(Actor actor, UUID requestId, String reason) {
        requireAdmin(actor);
        RevivalRequest request = load(requestId);
        request.reject(actor.userId(), reason, clock.instant());
        return views(List.of(request)).getFirst();
    }

    /** The admin queue, oldest first. */
    @Transactional(readOnly = true)
    public PageResponse<RevivalRequestView> byStatus(Actor actor, RevivalStatus status, int page, int size) {
        requireAdmin(actor);
        Page<RevivalRequest> result = requests.findByStatus(status,
            PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.ASC, "createdAt")));
        return new PageResponse<>(views(result.getContent()), page, size, result.getTotalElements());
    }

    private List<RevivalRequestView> views(List<RevivalRequest> page) {
        Map<UUID, Club> byId = clubs.findByIdIn(page.stream().map(RevivalRequest::clubId).toList()).stream()
            .collect(Collectors.toMap(Club::id, Function.identity()));
        Map<UUID, UserSummary> people = users.findByIds(page.stream().map(RevivalRequest::requesterId).toList());
        return page.stream().map(r -> {
            Club club = byId.get(r.clubId());
            UserSummary requester = people.get(r.requesterId());
            return new RevivalRequestView(r.id(), r.clubId(), club == null ? null : club.name(),
                club == null ? null : club.slug(), r.requesterId(), requester == null ? null : requester.displayName(),
                r.motivation(), r.status().name(), r.reviewNote(), r.createdAt());
        }).toList();
    }

    private RevivalRequest load(UUID requestId) {
        return requests.findById(requestId)
            .orElseThrow(() -> new NotFoundException("REVIVAL_NOT_FOUND", "Revival request not found"));
    }

    private static void requireAdmin(Actor actor) {
        if (!actor.admin()) {
            throw new ForbiddenException("ADMIN_ONLY", "Only admins can do this.");
        }
    }
}
