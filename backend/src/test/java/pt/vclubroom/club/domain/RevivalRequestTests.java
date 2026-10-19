package pt.vclubroom.club.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pt.vclubroom.shared.error.BusinessRuleException;

/** A revival request is decided once: approved or rejected, never both, never twice. */
class RevivalRequestTests {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00.123456789Z");

    private RevivalRequest submitted() {
        return RevivalRequest.submit(UUID.randomUUID(), UUID.randomUUID(), "We have a new promo", NOW);
    }

    @Test
    void aNewRequestWaitsAndIsApprovedOnce() {
        RevivalRequest request = submitted();
        assertThat(request.status()).isEqualTo(RevivalStatus.PENDING);
        assertThat(request.createdAt()).isEqualTo(Instant.parse("2026-10-19T10:00:00.123456Z"));

        request.approve(UUID.randomUUID(), NOW);
        assertThat(request.status()).isEqualTo(RevivalStatus.APPROVED);
        assertThatThrownBy(() -> request.reject(UUID.randomUUID(), "Too late", NOW))
            .isInstanceOf(BusinessRuleException.class).hasFieldOrPropertyWithValue("code", "REVIVAL_NOT_PENDING");
    }

    @Test
    void aRejectionKeepsItsReasonAndIsFinal() {
        RevivalRequest request = submitted();
        request.reject(UUID.randomUUID(), "Nobody to run it", NOW);
        assertThat(request.status()).isEqualTo(RevivalStatus.REJECTED);
        assertThat(request.reviewNote()).isEqualTo("Nobody to run it");
        assertThatThrownBy(() -> request.approve(UUID.randomUUID(), NOW)).isInstanceOf(BusinessRuleException.class);
    }
}
