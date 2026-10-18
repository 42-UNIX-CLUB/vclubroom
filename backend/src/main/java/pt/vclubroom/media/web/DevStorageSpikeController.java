package pt.vclubroom.media.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.media.application.MediaProperties;
import pt.vclubroom.media.application.ObjectStorage;
import pt.vclubroom.media.application.PresignedUpload;

/**
 * E08-S01-T3 de-risking spike, dev profile only: presigned URLs to try an upload by hand through the Vite proxy
 * (later through nginx). E08-S02's upload slots replace it; delete this class then.
 */
@RestController
@Profile("dev")
class DevStorageSpikeController {

    private final ObjectStorage storage;
    private final MediaProperties properties;

    DevStorageSpikeController(ObjectStorage storage, MediaProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    @GetMapping("/api/dev/storage-spike")
    Map<String, Object> spike() {
        String key = "spike/" + UUID.randomUUID() + ".txt";
        PresignedUpload privateUpload = storage.presignUpload(properties.privateBucket(), key, "text/plain", properties.uploadUrlTtl());
        PresignedUpload publicUpload = storage.presignUpload(properties.publicBucket(), key, "text/plain", properties.uploadUrlTtl());
        return Map.of(
            "contentType", "text/plain",
            "private", Map.of("put", privateUpload.url(),
                "get", storage.presignDownload(properties.privateBucket(), key, properties.downloadUrlTtl()),
                "unsigned", storage.publicUrl(properties.privateBucket(), key)),
            "public", Map.of("put", publicUpload.url(), "get", storage.publicUrl(properties.publicBucket(), key)),
            "signedHeaders", List.copyOf(privateUpload.headers().keySet()));
    }
}
