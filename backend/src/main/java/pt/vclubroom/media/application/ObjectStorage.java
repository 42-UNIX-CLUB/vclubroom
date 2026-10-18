package pt.vclubroom.media.application;

import java.time.Duration;
import java.util.Optional;

/**
 * Port: everything the media module needs from an object store. The adapter (infrastructure.S3ObjectStorage)
 * talks to SeaweedFS over the S3 API; replacing it with AWS S3 or R2 touches only the adapter or configuration.
 */
public interface ObjectStorage {

    PresignedUpload presignUpload(String bucket, String key, String contentType, Duration ttl);

    String presignDownload(String bucket, String key, Duration ttl);

    String publicUrl(String bucket, String key);

    Optional<StoredObject> head(String bucket, String key);

    void delete(String bucket, String key);

    void ensureBucket(String bucket);
}
