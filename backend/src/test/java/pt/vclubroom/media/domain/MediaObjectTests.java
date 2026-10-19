package pt.vclubroom.media.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pt.vclubroom.media.api.MediaKind;
import pt.vclubroom.media.api.MediaPurpose;
import pt.vclubroom.shared.error.BusinessRuleException;

class MediaObjectTests {

    private static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");
    private final UUID owner = UUID.randomUUID();

    private MediaObject photo() {
        return MediaObject.pending(owner, MediaPurpose.POST_ATTACHMENT, MediaKind.IMAGE, "image/png", 1000, "cat.png",
            "media-private", NOW);
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void theKeyNamesThePurposeTheMonthAndTheId() {
        MediaObject m = photo();
        assertThat(m.storageKey()).isEqualTo("post_attachment/2026/10/" + m.id());
        assertThat(m.status()).isEqualTo(MediaStatus.PENDING);
    }

    @Test
    void anUploadIsCompletedOnceWithTheVerifiedValues() {
        MediaObject m = photo();
        m.markReady(987, "image/png");
        assertThat(m.sizeBytes()).isEqualTo(987);
        assertCode(() -> m.markReady(987, "image/png"), "MEDIA_NOT_PENDING");
    }

    @Test
    void onlyTheUploaderAttachesAReadyFileForItsPurpose() {                    // BR-19
        MediaObject m = photo();
        assertCode(() -> m.attach(owner, MediaPurpose.POST_ATTACHMENT, NOW), "MEDIA_NOT_ATTACHABLE");   // still PENDING
        m.markReady(1000, "image/png");
        assertCode(() -> m.attach(UUID.randomUUID(), MediaPurpose.POST_ATTACHMENT, NOW), "MEDIA_NOT_ATTACHABLE");
        assertCode(() -> m.attach(owner, MediaPurpose.CHAT_ATTACHMENT, NOW), "MEDIA_NOT_ATTACHABLE");

        m.attach(owner, MediaPurpose.POST_ATTACHMENT, NOW);
        m.attach(owner, MediaPurpose.POST_ATTACHMENT, NOW);                     // a retry is harmless
        assertThat(m.status()).isEqualTo(MediaStatus.ATTACHED);
    }

    @Test
    void aDeletedFileCannotBeAttached() {
        MediaObject m = photo();
        m.markReady(1000, "image/png");
        m.markDeleted();
        assertCode(() -> m.attach(owner, MediaPurpose.POST_ATTACHMENT, NOW), "MEDIA_NOT_ATTACHABLE");
    }
}
