package pt.vclubroom.media.infrastructure;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pt.vclubroom.media.application.MediaProperties;
import pt.vclubroom.media.application.ObjectStorage;

/**
 * Creates the two buckets on startup if missing (idempotent, safe with N instances).
 * Tests without SeaweedFS set app.storage.init-buckets=false (src/test/resources/application.properties).
 */
@Component
@ConditionalOnProperty(name = "app.storage.init-buckets", havingValue = "true", matchIfMissing = true)
class BucketInitializer implements ApplicationRunner {

    private final ObjectStorage storage;
    private final MediaProperties properties;

    BucketInitializer(ObjectStorage storage, MediaProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        storage.ensureBucket(properties.publicBucket());
        storage.ensureBucket(properties.privateBucket());
    }
}
