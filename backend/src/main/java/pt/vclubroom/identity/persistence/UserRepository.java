package pt.vclubroom.identity.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.vclubroom.identity.api.PlatformRole;
import pt.vclubroom.identity.domain.User;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByFtId(Long ftId);

    boolean existsByLoginIgnoreCase(String login);

    Optional<User> findByLoginIgnoreCase(String login);

    @Query("select u from User u where lower(u.login) = lower(:login) and u.provider = pt.vclubroom.identity.domain.AuthProvider.LOCAL")
    Optional<User> findLocalByLogin(@Param("login") String login);

    List<User> findByIdIn(Collection<UUID> ids);

    @Query("select u.id from User u where u.platformRole = :role and u.status = pt.vclubroom.identity.domain.UserStatus.ACTIVE")
    List<UUID> findIdsByRole(@Param("role") PlatformRole role);

    @Query("""
           select u from User u
           where u.status = pt.vclubroom.identity.domain.UserStatus.ACTIVE
             and (lower(u.login) like lower(concat(:q, '%')) or lower(u.displayName) like lower(concat('%', :q, '%')))
           order by u.login
           """)
    List<User> search(@Param("q") String query, Limit limit);

    /** The admin list: every account, whatever its status; an empty query lists them all. */
    @Query("""
           select u from User u
           where :q = '' or lower(u.login) like lower(concat(:q, '%')) or lower(u.displayName) like lower(concat('%', :q, '%'))
           """)
    Page<User> searchAll(@Param("q") String query, Pageable pageable);
}
