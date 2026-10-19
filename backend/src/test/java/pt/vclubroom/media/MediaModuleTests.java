package pt.vclubroom.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.media.api.MediaCatalog;
import pt.vclubroom.media.api.MediaKind;
import pt.vclubroom.media.api.MediaPurpose;
import pt.vclubroom.media.api.MediaView;
import pt.vclubroom.media.application.MediaUploadService;
import pt.vclubroom.media.application.ObjectStorage;
import pt.vclubroom.media.application.PresignedUpload;
import pt.vclubroom.media.application.StoredObject;
import pt.vclubroom.media.domain.MediaStatus;
import pt.vclubroom.media.persistence.MediaObjectRepository;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;

/** The media module on its own (E08-S02). The storage port is mocked: the shared test setup has no SeaweedFS. */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class MediaModuleTests {

    @MockitoBean
    ObjectStorage storage;

    @Autowired
    MediaUploadService uploads;

    @Autowired
    MediaCatalog catalog;

    @Autowired
    MediaObjectRepository media;

    @BeforeEach
    void signing() {
        given(storage.presignUpload(anyString(), anyString(), anyString(), any()))
            .willAnswer(call -> new PresignedUpload("http://app/" + call.getArgument(0) + "/" + call.getArgument(1),
                Map.of("content-type", List.of(call.<String>getArgument(2))), Instant.now().plusSeconds(600)));
        given(storage.presignDownload(anyString(), anyString(), any())).willReturn("http://app/signed-get");
        given(storage.publicUrl(anyString(), anyString())).willAnswer(call -> "http://app/" + call.getArgument(0) + "/" + call.getArgument(1));
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    /** An uploaded, verified file of this owner. */
    private UUID readyPhoto(Actor owner, MediaPurpose purpose) {
        MediaUploadService.UploadSlot slot = uploads.createSlot(owner, purpose, "cat.png", "image/png", 1000);
        given(storage.head(eq(purpose.isPublic() ? "media-public" : "media-private"), anyString()))
            .willReturn(Optional.of(new StoredObject(1000, "image/png")));
        uploads.complete(owner, slot.mediaId());
        return slot.mediaId();
    }

    @Test
    void aSlotIsCheckedAgainstTheLimitsAndSignedForTheRightBucket() {
        Actor owner = new Actor(UUID.randomUUID(), false);
        assertCode(() -> uploads.createSlot(owner, MediaPurpose.POST_ATTACHMENT, "notes.txt", "text/plain", 10), "MEDIA_TYPE_NOT_ALLOWED");
        assertCode(() -> uploads.createSlot(owner, MediaPurpose.CLUB_AVATAR, "clip.mp4", "video/mp4", 10), "MEDIA_KIND_NOT_ALLOWED");
        assertCode(() -> uploads.createSlot(owner, MediaPurpose.POST_ATTACHMENT, "big.png", "image/png", 11 * 1024 * 1024), "MEDIA_TOO_LARGE");

        MediaUploadService.UploadSlot slot = uploads.createSlot(owner, MediaPurpose.POST_ATTACHMENT, "clip.mp4", "video/mp4", 50_000_000);
        assertThat(slot.upload().url()).startsWith("http://app/media-private/post_attachment/");
        assertThat(media.findById(slot.mediaId()).orElseThrow().status()).isEqualTo(MediaStatus.PENDING);
    }

    @Test
    void completingVerifiesWhatStorageHolds() {
        Actor owner = new Actor(UUID.randomUUID(), false);
        UUID id = uploads.createSlot(owner, MediaPurpose.POST_ATTACHMENT, "song.mp3", "audio/mpeg", 1000).mediaId();
        given(storage.head(anyString(), anyString())).willReturn(Optional.empty());
        assertCode(() -> uploads.complete(owner, id), "MEDIA_NOT_UPLOADED");
        assertThatThrownBy(() -> uploads.complete(new Actor(UUID.randomUUID(), false), id)).isInstanceOf(NotFoundException.class);

        given(storage.head(anyString(), anyString())).willReturn(Optional.of(new StoredObject(900, "audio/mpeg")));
        assertThat(uploads.complete(owner, id).status()).isEqualTo(MediaStatus.READY);
    }

    @Test
    void aFileThatDoesNotMatchIsDeletedAndStaysDeleted() {
        Actor owner = new Actor(UUID.randomUUID(), false);
        UUID id = uploads.createSlot(owner, MediaPurpose.POST_ATTACHMENT, "cat.png", "image/png", 1000).mediaId();
        given(storage.head(anyString(), anyString())).willReturn(Optional.of(new StoredObject(20 * 1024 * 1024, "image/png")));

        assertCode(() -> uploads.complete(owner, id), "MEDIA_REJECTED");
        verify(storage).delete(eq("media-private"), anyString());
        assertThat(media.findById(id).orElseThrow().status()).isEqualTo(MediaStatus.DELETED);   // committed despite the 422
    }

    @Test
    void attachingFollowsBr19AndPrivateFilesGetSignedUrls() {
        Actor owner = new Actor(UUID.randomUUID(), false);
        UUID id = readyPhoto(owner, MediaPurpose.POST_ATTACHMENT);

        assertCode(() -> catalog.attach(List.of(id), UUID.randomUUID(), MediaPurpose.POST_ATTACHMENT), "MEDIA_NOT_ATTACHABLE");
        assertCode(() -> catalog.attach(List.of(id, UUID.randomUUID()), owner.userId(), MediaPurpose.POST_ATTACHMENT), "MEDIA_NOT_ATTACHABLE");
        List<MediaView> attached = catalog.attach(List.of(id), owner.userId(), MediaPurpose.POST_ATTACHMENT);
        assertThat(attached).singleElement().satisfies(view -> {
            assertThat(view.kind()).isEqualTo(MediaKind.IMAGE);
            assertThat(view.url()).isEqualTo("http://app/signed-get");
        });
        catalog.attach(List.of(id), owner.userId(), MediaPurpose.POST_ATTACHMENT);            // retry: no error
        assertThat(media.findById(id).orElseThrow().status()).isEqualTo(MediaStatus.ATTACHED);
    }

    @Test
    void publicFilesResolveToStableUrlsAndUnknownIdsAreLeftOut() {
        Actor owner = new Actor(UUID.randomUUID(), false);
        UUID avatar = readyPhoto(owner, MediaPurpose.CLUB_AVATAR);

        Map<UUID, MediaView> views = catalog.resolve(List.of(avatar, UUID.randomUUID()));
        assertThat(views).containsOnlyKeys(avatar);
        assertThat(views.get(avatar).url()).startsWith("http://app/media-public/club_avatar/");
    }
}
