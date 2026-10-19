package pt.vclubroom.discovery.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.vclubroom.discovery.domain.ClubCard;

/** Idempotent upserts: the projector may receive an event twice, or events out of order. E09 adds bumpLastActivity. */
public interface ClubCardRepository extends JpaRepository<ClubCard, UUID> {

    Optional<ClubCard> findBySlug(String slug);

    /** A profile only replaces an older one: an event that arrives late never overwrites newer data. */
    @Modifying
    @Query(value = """
           insert into discovery.club_cards (club_id, slug, name, tagline, description, category, tags, avatar_url,
                  banner_url, status, join_policy, current_era, search_document, member_count, updated_at)
           values (:clubId, :slug, :name, :tagline, :description, :category, :tags, :avatarUrl,
                  :bannerUrl, :status, :joinPolicy, :currentEra, :searchDocument, 0, :occurredAt)
           on conflict (club_id) do update set
                  slug = excluded.slug, name = excluded.name, tagline = excluded.tagline,
                  description = excluded.description, category = excluded.category, tags = excluded.tags,
                  avatar_url = excluded.avatar_url, banner_url = excluded.banner_url, status = excluded.status,
                  join_policy = excluded.join_policy, current_era = excluded.current_era,
                  search_document = excluded.search_document, updated_at = excluded.updated_at
           where discovery.club_cards.updated_at <= excluded.updated_at
           """, nativeQuery = true)
    void upsertProfile(@Param("clubId") UUID clubId, @Param("slug") String slug, @Param("name") String name,
                       @Param("tagline") String tagline, @Param("description") String description,
                       @Param("category") String category, @Param("tags") String[] tags,
                       @Param("avatarUrl") String avatarUrl, @Param("bannerUrl") String bannerUrl,
                       @Param("status") String status, @Param("joinPolicy") String joinPolicy,
                       @Param("currentEra") int currentEra, @Param("searchDocument") String searchDocument,
                       @Param("occurredAt") Instant occurredAt);

    /** Creates a placeholder card if needed (updated_at = -infinity so the next profile upsert always wins). */
    @Modifying
    @Query(value = """
           insert into discovery.club_cards (club_id, slug, name, status, member_count, founded_at, updated_at)
           values (:clubId, :slug, :name, 'ACTIVE', 0, :at, '-infinity')
           on conflict (club_id) do update set founded_at = coalesce(discovery.club_cards.founded_at, excluded.founded_at)
           """, nativeQuery = true)
    void upsertFoundedAt(@Param("clubId") UUID clubId, @Param("slug") String slug, @Param("name") String name,
                         @Param("at") Instant at);

    @Modifying
    @Query(value = "update discovery.club_cards set member_count = :count where club_id = :clubId", nativeQuery = true)
    void setMemberCount(@Param("clubId") UUID clubId, @Param("count") int count);
}
