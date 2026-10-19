package pt.vclubroom.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.club.api.JoinPolicy;
import pt.vclubroom.club.events.ClubApproved;
import pt.vclubroom.club.events.ClubProfileUpdated;
import pt.vclubroom.discovery.domain.ClubCard;
import pt.vclubroom.discovery.persistence.ClubCardRepository;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.membership.events.MemberJoined;

/** The discovery module started on its own: how events become club cards (E06-S01-T2). */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class ClubCardProjectorModuleTests {

    @MockitoBean
    ClubAuthorization clubAuthorization;       // from module "membership": mocked, that module is not started

    @Autowired
    ClubCardRepository cards;

    @Autowired
    CompletedEventPublications completed;

    // @ApplicationModuleTest runs all tests on ONE instance of this class (PER_CLASS): fields are shared,
    // so every test gets its own club here instead of in a field initialiser.
    private UUID clubId;
    private String slug;

    @BeforeEach
    void aNewClubWithOneMember() {
        clubId = UUID.randomUUID();
        slug = "chess-" + clubId.toString().substring(0, 8);
        given(clubAuthorization.activeMemberIds(any())).willReturn(Set.of(UUID.randomUUID()));
    }

    private ClubProfileUpdated profile(String name, ClubStatus status, Instant at) {
        return new ClubProfileUpdated(clubId, slug, name, "Blitz on Fridays", "We play chess", "Games",
            List.of("chess", "blitz"), null, null, JoinPolicy.APPROVAL, status, 1, at);
    }

    private Optional<ClubCard> card() {
        return cards.findById(clubId);
    }

    /** How many events of this club the projector finished handling (the event publication registry). */
    private long handled() {
        return completed.findAll().stream().filter(p -> switch (p.getEvent()) {
            case ClubProfileUpdated e -> e.clubId().equals(clubId);
            case ClubApproved e -> e.clubId().equals(clubId);
            case MemberJoined e -> e.clubId().equals(clubId);
            default -> false;
        }).count();
    }

    @Test
    void anActiveProfileBecomesACardWithItsMemberCount(Scenario scenario) {
        scenario.publish(profile("Chess Club", ClubStatus.ACTIVE, Instant.now()))
            .andWaitForStateChange(this::card, Optional::isPresent)
            .andVerify(card -> {
                assertThat(card.get().name()).isEqualTo("Chess Club");
                assertThat(card.get().tags()).containsExactly("chess", "blitz");
                assertThat(card.get().status()).isEqualTo("ACTIVE");
                assertThat(card.get().memberCount()).isEqualTo(1);
            });
    }

    @Test
    void pendingClubsNeverGetACard(Scenario scenario) {
        scenario.publish(profile("Secret Club", ClubStatus.PENDING_APPROVAL, Instant.now()))
            .andWaitForStateChange(this::handled, times -> times == 1)
            .andVerify(times -> assertThat(card()).isEmpty());                  // BR-03
    }

    @Test
    void aLateOlderProfileDoesNotOverwriteANewerOne(Scenario scenario) {
        Instant now = Instant.now();
        scenario.publish(profile("New name", ClubStatus.ACTIVE, now)).andWaitForStateChange(this::card, Optional::isPresent);
        scenario.publish(profile("Old name", ClubStatus.ACTIVE, now.minusSeconds(60)))
            .andWaitForStateChange(this::handled, times -> times == 2)
            .andVerify(times -> assertThat(card().orElseThrow().name()).isEqualTo("New name"));
    }

    @Test
    void approvalBeforeTheProfileLeavesAPlaceholderThatTheProfileCompletes(Scenario scenario) {
        Instant approvedAt = Instant.parse("2026-10-18T10:00:00Z");
        scenario.publish(new ClubApproved(clubId, slug, "Chess Club", UUID.randomUUID(), UUID.randomUUID(), 1, approvedAt))
            .andWaitForStateChange(this::card, Optional::isPresent);
        scenario.publish(profile("Chess Club", ClubStatus.ACTIVE, Instant.now()))
            .andWaitForStateChange(() -> card().map(ClubCard::category).orElse(null), category -> category != null)
            .andVerify(category -> {
                assertThat(category).isEqualTo("Games");
                assertThat(card().orElseThrow().foundedAt()).isEqualTo(approvedAt);
            });
    }

    @Test
    void memberCountsAreRecountedNotIncremented(Scenario scenario) {
        scenario.publish(profile("Chess Club", ClubStatus.ACTIVE, Instant.now())).andWaitForStateChange(this::card, Optional::isPresent);
        given(clubAuthorization.activeMemberIds(clubId)).willReturn(Set.of(UUID.randomUUID(), UUID.randomUUID()));
        MemberJoined joined = new MemberJoined(clubId, UUID.randomUUID(), UUID.randomUUID(), "OPEN_JOIN", Instant.now());

        scenario.publish(joined).andWaitForStateChange(() -> card().orElseThrow().memberCount(), count -> count == 2);
        scenario.publish(joined)                                                // delivered again
            .andWaitForStateChange(this::handled, times -> times == 3)
            .andVerify(times -> assertThat(card().orElseThrow().memberCount()).isEqualTo(2));
    }
}
