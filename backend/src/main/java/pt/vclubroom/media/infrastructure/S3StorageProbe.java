package pt.vclubroom.media.infrastructure;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pt.vclubroom.media.api.StorageProbe;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;

/** {@link StorageProbe} on SeaweedFS: it answers for the public bucket within 2 s. */
@Component
class S3StorageProbe implements StorageProbe {

    private final S3Client s3;
    private final String bucket;

    S3StorageProbe(S3Client s3, @Value("${app.media.public-bucket}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public boolean reachable() {
        try {
            s3.headBucket(b -> b.bucket(bucket).overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(2))));
            return true;
        } catch (SdkException e) {
            return false;
        }
    }
}
