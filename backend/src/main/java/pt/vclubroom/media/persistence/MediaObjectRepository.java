package pt.vclubroom.media.persistence;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.vclubroom.media.domain.MediaObject;

/** E08-S03 adds claimOrphans (the cleanup of uploads that were never completed or attached). */
public interface MediaObjectRepository extends JpaRepository<MediaObject, UUID> {

    /** Row locks: two posts attaching the same file at once cannot both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MediaObject m where m.id in :ids")
    List<MediaObject> findAllForUpdate(@Param("ids") Collection<UUID> ids);

    @Query("select m from MediaObject m where m.id in :ids and m.status <> pt.vclubroom.media.domain.MediaStatus.DELETED")
    List<MediaObject> findLive(@Param("ids") Collection<UUID> ids);
}
