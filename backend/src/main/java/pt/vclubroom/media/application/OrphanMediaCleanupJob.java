package pt.vclubroom.media.application;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.media.domain.MediaObject;
import pt.vclubroom.media.persistence.MediaObjectRepository;

/**
 * Deletes uploads nobody completed or used within app.media.orphan-ttl (media.md §8.3): from storage first, then the
 * row is marked DELETED. Safe with several backends (SKIP LOCKED). A file storage refuses to delete stays as it is
 * and is tried again on the next run.
 */
@Component
class OrphanMediaCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(OrphanMediaCleanupJob.class);
    static final int BATCH = 100;

    private final MediaObjectRepository media;
    private final ObjectStorage storage;
    private final MediaProperties properties;
    private final Clock clock;

    OrphanMediaCleanupJob(MediaObjectRepository media, ObjectStorage storage, MediaProperties properties, Clock clock) {
        this.media = media;
        this.storage = storage;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.media.orphan-cleanup-interval:PT1H}", initialDelayString = "${app.media.orphan-cleanup-interval:PT1H}")
    @Transactional
    public void cleanUp() {
        int deleted = 0;
        for (MediaObject orphan : media.claimOrphans(clock.instant().minus(properties.orphanTtl()), BATCH)) {
            try {
                storage.delete(orphan.bucket(), orphan.storageKey());
                orphan.markDeleted();
                deleted++;
            } catch (RuntimeException e) {
                log.warn("Could not delete orphan media {} ({}/{}): {}", orphan.id(), orphan.bucket(), orphan.storageKey(), e.getMessage());
            }
        }
        if (deleted > 0) {
            log.info("Deleted {} orphan media objects", deleted);
        }
    }
}
