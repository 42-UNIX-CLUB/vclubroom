package pt.vclubroom.media.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.vclubroom.media.domain.MediaObject;

public interface MediaObjectRepository extends JpaRepository<MediaObject, UUID> {

    /** Row locks: two posts attaching the same file at once cannot both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MediaObject m where m.id in :ids")
    List<MediaObject> findAllForUpdate(@Param("ids") Collection<UUID> ids);

    @Query("select m from MediaObject m where m.id in :ids and m.status <> pt.vclubroom.media.domain.MediaStatus.DELETED")
    List<MediaObject> findLive(@Param("ids") Collection<UUID> ids);

    /**
     * Uploads never completed (PENDING) or never used (READY) since before cutoff, oldest first. SKIP LOCKED: with
     * several backends, each claims different rows and none waits.
     */
    @Query(value = """
           select * from media.media_objects
           where status in ('PENDING', 'READY') and created_at < :cutoff
           order by created_at
           limit :batch
           for update skip locked
           """, nativeQuery = true)
    List<MediaObject> claimOrphans(@Param("cutoff") Instant cutoff, @Param("batch") int batch);
}
