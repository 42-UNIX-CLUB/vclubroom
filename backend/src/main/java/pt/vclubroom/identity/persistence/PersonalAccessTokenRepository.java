package pt.vclubroom.identity.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.vclubroom.identity.domain.PersonalAccessToken;

public interface PersonalAccessTokenRepository extends JpaRepository<PersonalAccessToken, UUID> {

    Optional<PersonalAccessToken> findByTokenHash(String tokenHash);

    /** Revoked tokens leave the list; expired ones stay until revoked, marked as expired. */
    List<PersonalAccessToken> findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(UUID userId);

    @Query("""
           select count(t) from PersonalAccessToken t
           where t.userId = :userId and t.revokedAt is null and (t.expiresAt is null or t.expiresAt > :now)
           """)
    long countActive(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("""
           select t from PersonalAccessToken t
           where t.revokedAt is null and t.remindedAt is null and t.expiresAt > :now and t.expiresAt <= :until
           """)
    List<PersonalAccessToken> findToRemind(@Param("now") Instant now, @Param("until") Instant until);

    @Modifying
    @Query("update PersonalAccessToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAll(@Param("userId") UUID userId, @Param("now") Instant now);
}
