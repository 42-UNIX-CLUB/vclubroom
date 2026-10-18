package pt.vclubroom.media.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pt.vclubroom.media.application.PresignedUpload;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** The S3 adapter against a real SeaweedFS (same image as compose.yaml), without starting Spring. */
@Testcontainers
class S3ObjectStorageTests {

    /** Same shape as deploy/seaweedfs/s3.json: a backend identity, and anonymous read on media-public only. */
    private static final String S3_JSON = """
        {"identities": [
          {"name": "backend", "credentials": [{"accessKey": "test", "secretKey": "test-secret"}],
           "actions": ["Admin", "Read", "Write", "List", "Tagging"]},
          {"name": "anonymous", "actions": ["Read:media-public"]}
        ]}
        """;

    @Container
    static final GenericContainer<?> seaweed = new GenericContainer<>("chrislusf/seaweedfs:4.48")
        .withCommand("server -dir=/data -s3 -s3.port=8333 -s3.config=/etc/seaweedfs/s3.json")
        .withCopyToContainer(Transferable.of(S3_JSON), "/etc/seaweedfs/s3.json")
        .withExposedPorts(8333)
        .waitingFor(Wait.forHttp("/").forPort(8333).forStatusCodeMatching(code -> code < 500))
        .withStartupTimeout(Duration.ofMinutes(2));

    private static final HttpClient http = HttpClient.newHttpClient();
    private static S3Client s3;
    private static S3Presigner presigner;
    private static S3ObjectStorage storage;

    @BeforeAll
    static void connect() {
        String endpoint = "http://" + seaweed.getHost() + ":" + seaweed.getMappedPort(8333);
        StorageProperties properties = new StorageProperties(endpoint, endpoint, "us-east-1", "test", "test-secret", false);
        S3Config config = new S3Config();
        s3 = config.s3Client(properties);
        presigner = config.s3Presigner(properties);
        storage = new S3ObjectStorage(s3, presigner, properties);
        storage.ensureBucket("media-public");
        storage.ensureBucket("media-private");
    }

    @AfterAll
    static void close() {
        presigner.close();
        s3.close();
    }

    @Test
    void creatingTheBucketsIsIdempotent() {
        storage.ensureBucket("media-public");                  // second time: no error (N instances start together)
        storage.ensureBucket("media-private");
        assertThat(s3.listBuckets().buckets()).extracting(b -> b.name()).contains("media-public", "media-private");
    }

    @Test
    void aBrowserCanUploadAndDownloadWithPresignedUrls() throws Exception {
        String key = "test/" + UUID.randomUUID() + ".txt";
        PresignedUpload upload = storage.presignUpload("media-private", key, "text/plain", Duration.ofMinutes(5));

        HttpResponse<String> put = http.send(HttpRequest.newBuilder(URI.create(upload.url()))
            .header("Content-Type", "text/plain")                  // a signed header: must match exactly
            .PUT(HttpRequest.BodyPublishers.ofString("hello clubroom")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(put.statusCode()).isEqualTo(200);

        assertThat(storage.head("media-private", key)).hasValueSatisfying(stored -> {
            assertThat(stored.sizeBytes()).isEqualTo(14);
            assertThat(stored.contentType()).isEqualTo("text/plain");
        });
        String download = storage.presignDownload("media-private", key, Duration.ofMinutes(5));
        assertThat(get(download).body()).isEqualTo("hello clubroom");
    }

    @Test
    void anonymousVisitorsReadThePublicBucketOnly() throws Exception {
        String key = "test/" + UUID.randomUUID() + ".txt";
        for (String bucket : new String[] {"media-public", "media-private"}) {
            PresignedUpload upload = storage.presignUpload(bucket, key, "text/plain", Duration.ofMinutes(5));
            http.send(HttpRequest.newBuilder(URI.create(upload.url())).header("Content-Type", "text/plain")
                .PUT(HttpRequest.BodyPublishers.ofString("avatar")).build(), HttpResponse.BodyHandlers.discarding());
        }

        assertThat(get(storage.publicUrl("media-public", key)).statusCode()).isEqualTo(200);   // avatars, banners
        assertThat(get(storage.publicUrl("media-private", key)).statusCode()).isEqualTo(403);  // attachments need a signature
    }

    @Test
    void aMissingObjectHasNoMetadata() {
        assertThat(storage.head("media-private", "nothing/here.txt")).isEmpty();
    }

    private static HttpResponse<String> get(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
