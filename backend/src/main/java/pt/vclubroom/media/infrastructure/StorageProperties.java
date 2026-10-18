package pt.vclubroom.media.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * app.storage.* in application.yml. endpoint is how the backend reaches SeaweedFS (dev: http://localhost:8333,
 * Docker: http://seaweedfs:8333); publicEndpoint is the origin browsers use, which presigned URLs are signed for.
 *
 * @param initBuckets create the buckets at startup (BucketInitializer); tests without SeaweedFS turn it off. Declared
 *                    here so the property is in the configuration metadata (no "Cannot resolve" in the IDE).
 */
@ConfigurationProperties("app.storage")
public record StorageProperties(String endpoint, String publicEndpoint, String region,
                                String accessKey, String secretKey, @DefaultValue("true") boolean initBuckets) {
}
