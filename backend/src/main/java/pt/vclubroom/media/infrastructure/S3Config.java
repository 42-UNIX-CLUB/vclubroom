package pt.vclubroom.media.infrastructure;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
class S3Config {

    /** Backend → SeaweedFS (internal network address). */
    @Bean(destroyMethod = "close")
    S3Client s3Client(StorageProperties p) {
        return S3Client.builder()
            .endpointOverride(URI.create(p.endpoint()))
            .region(Region.of(p.region()))
            .credentialsProvider(credentials(p))
            .forcePathStyle(true)
            // SDK ≥ 2.30 sends CRC checksums by default; many S3-compatible servers reject them.
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .build();
    }

    /** Signs URLs for the PUBLIC origin (what browsers reach through the Vite proxy or nginx). */
    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(StorageProperties p) {
        return S3Presigner.builder()
            .endpointOverride(URI.create(p.publicEndpoint()))
            .region(Region.of(p.region()))
            .credentialsProvider(credentials(p))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();
    }

    private static StaticCredentialsProvider credentials(StorageProperties p) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(p.accessKey(), p.secretKey()));
    }
}
