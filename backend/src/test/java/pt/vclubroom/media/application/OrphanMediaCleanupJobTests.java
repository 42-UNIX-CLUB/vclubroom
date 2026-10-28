package pt.vclubroom.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.media.api.MediaCatalog;
import pt.vclubroom.media.api.MediaPurpose;
import pt.vclubroom.media.domain.MediaStatus;
import pt.vclubroom.media.persistence.MediaObjectRepository;
import pt.vclubroom.shared.security.Actor;

/** The cleanup of abandoned uploads (E08-S03), with the storage port mocked. The test runs the job itself. */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.media.orphan-cleanup-interval=PT24H")
class OrphanMediaCleanupJobTests {

    @MockitoBean
    ObjectStorage storage;

    @Autowired
    MediaUploadService uploads;

    @Autowired
    MediaCatalog catalog;

    @Autowired
    MediaObjectRepository media;

    @Autowired
    OrphanMediaCleanupJob job;

    @Autowired
    JdbcTemplate jdbc;

    private final Actor owner = new Actor(UUID.randomUUID(), false);

    @BeforeEach
    void storage() {
        given(storage.presignUpload(anyString(), anyString(), anyString(), any()))
            .willAnswer(call -> new PresignedUpload("http://app/" + call.getArgument(1), Map.of(), Instant.now().plusSeconds(600)));
        given(storage.head(anyString(), anyString())).willReturn(Optional.of(new StoredObject(1000, "image/png")));
    }

    private UUID pending() {
        return uploads.createSlot(owner, MediaPurpose.POST_ATTACHMENT, "cat.png", "image/png", 1000).mediaId();
    }

    private UUID ready() {
        UUID id = pending();
        uploads.complete(owner, id);
        return id;
    }

    private void hoursOld(UUID id, int hours) {
        jdbc.update("update media.media_objects set created_at = now() - make_interval(hours => ?) where id = ?", hours, id);
    }

    private MediaStatus status(UUID id) {
        return media.findById(id).orElseThrow().status();
    }

    private String key(UUID id) {
        return media.findById(id).orElseThrow().storageKey();
    }

    @Test
    void uploadsLeftPendingOrUnusedForADayAreDeleted() {
        UUID abandoned = pending();
        UUID unused = ready();
        UUID used = ready();
        catalog.attach(List.of(used), owner.userId(), MediaPurpose.POST_ATTACHMENT);
        UUID recent = pending();
        for (UUID id : List.of(abandoned, unused, used)) hoursOld(id, 25);
        hoursOld(recent, 23);

        job.cleanUp();

        assertThat(status(abandoned)).isEqualTo(MediaStatus.DELETED);
        assertThat(status(unused)).isEqualTo(MediaStatus.DELETED);
        assertThat(status(used)).isEqualTo(MediaStatus.ATTACHED);                       // in a post: never touched
        assertThat(status(recent)).isEqualTo(MediaStatus.PENDING);                      // still within its day
        verify(storage).delete("media-private", key(abandoned));
        verify(storage).delete("media-private", key(unused));
        verify(storage, never()).delete(anyString(), eq(key(used)));
        verify(storage, never()).delete(anyString(), eq(key(recent)));
    }

    @Test
    void aFileStorageRefusesToDeleteIsTriedAgainNextTime() {
        UUID stuck = pending();
        UUID fine = pending();
        hoursOld(stuck, 30);
        hoursOld(fine, 30);
        willThrow(new RuntimeException("storage unavailable")).given(storage).delete(anyString(), eq(key(stuck)));

        job.cleanUp();
        assertThat(status(stuck)).isEqualTo(MediaStatus.PENDING);
        assertThat(status(fine)).isEqualTo(MediaStatus.DELETED);

        willDoNothing().given(storage).delete(anyString(), eq(key(stuck)));                // storage is back
        job.cleanUp();
        assertThat(status(stuck)).isEqualTo(MediaStatus.DELETED);
    }
}
