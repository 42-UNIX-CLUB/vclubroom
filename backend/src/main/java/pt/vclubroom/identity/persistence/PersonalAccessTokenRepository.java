package pt.vclubroom.identity.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.vclubroom.identity.domain.PersonalAccessToken;

public interface PersonalAccessTokenRepository extends JpaRepository<PersonalAccessToken, UUID> {

    Optional<PersonalAccessToken> findByTokenHash(String tokenHash);
}
