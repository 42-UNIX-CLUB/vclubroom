package pt.vclubroom.club.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.club.api.JoinPolicy;
import pt.vclubroom.shared.error.BusinessRuleException;

/** The club state machine: PENDING_APPROVAL → ACTIVE | REJECTED; ACTIVE → ARCHIVED → ACTIVE (next era). */
class ClubTests {

    private static final Instant NOW = Instant.parse("2026-10-18T10:00:00.123456789Z");
    private final UUID founder = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();

    private Club requested() {
        return Club.requestCreation(Slug.fromName("Chess Club"), "  Chess Club ", "Play every Friday", "**Welcome**",
            "Games", List.of("chess", "board-games"), founder, NOW);
    }

    @Test
    void aNewRequestIsPendingWithApprovalJoinPolicyAndNoEra() {
        Club club = requested();
        assertThat(club.status()).isEqualTo(ClubStatus.PENDING_APPROVAL);
        assertThat(club.joinPolicy()).isEqualTo(JoinPolicy.APPROVAL);
        assertThat(club.name()).isEqualTo("Chess Club");
        assertThat(club.currentEra()).isZero();
        assertThat(club.eras()).isEmpty();
        assertThat(club.createdAt()).isEqualTo(Instant.parse("2026-10-18T10:00:00.123456Z"));   // PostgreSQL precision
    }

    @Test
    void approvalActivatesTheClubAndStartsEraOne() {
        Club club = requested();
        club.approve(admin, NOW);
        assertThat(club.status()).isEqualTo(ClubStatus.ACTIVE);
        assertThat(club.currentEra()).isEqualTo(1);
        assertThat(club.eras()).singleElement().satisfies(era -> {
            assertThat(era.number()).isEqualTo(1);
            assertThat(era.startReason()).isEqualTo(EraStartReason.FOUNDED);
        });
    }

    @Test
    void rejectionIsFinal() {
        Club club = requested();
        club.reject(admin, "Duplicate of the Board Games club", NOW);
        assertThat(club.status()).isEqualTo(ClubStatus.REJECTED);
        assertThat(club.reviewNote()).isEqualTo("Duplicate of the Board Games club");
        assertThatThrownBy(() -> club.approve(admin, NOW)).isInstanceOfSatisfying(BusinessRuleException.class,
            e -> assertThat(e.code()).isEqualTo("CLUB_NOT_PENDING"));
    }

    @Test
    void archivalEndsTheEraAndRevivalStartsTheNextOne() {
        Club club = requested();
        club.approve(admin, NOW);
        club.archive(admin, "Inactive for a year", NOW.plusSeconds(60));
        assertThat(club.status()).isEqualTo(ClubStatus.ARCHIVED);
        assertThat(club.eras().get(0).endReason()).isEqualTo("Inactive for a year");

        club.revive(NOW.plusSeconds(120));
        assertThat(club.status()).isEqualTo(ClubStatus.ACTIVE);
        assertThat(club.currentEra()).isEqualTo(2);
        assertThat(club.eras()).extracting(ClubEra::startReason).containsExactly(EraStartReason.FOUNDED, EraStartReason.REVIVED);
    }

    @Test
    void onlyActiveClubsCanBeArchivedOrChangeJoinPolicy() {
        Club club = requested();
        assertThatThrownBy(() -> club.archive(admin, "x", NOW)).isInstanceOfSatisfying(BusinessRuleException.class,
            e -> assertThat(e.code()).isEqualTo("CLUB_NOT_ACTIVE"));
        assertThatThrownBy(() -> club.changeJoinPolicy(JoinPolicy.OPEN, NOW)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void pendingAndRejectedClubsAreVisibleOnlyToTheFounderAndAdmins() {
        Club club = requested();
        UUID stranger = UUID.randomUUID();
        assertThat(club.isVisibleTo(founder, false)).isTrue();
        assertThat(club.isVisibleTo(stranger, true)).isTrue();
        assertThat(club.isVisibleTo(stranger, false)).isFalse();

        club.approve(admin, NOW);
        assertThat(club.isVisibleTo(stranger, false)).isTrue();
    }
}