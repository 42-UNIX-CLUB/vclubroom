package pt.vclubroom.club;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.club.application.ClubDetails;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.application.CreateClubCommand;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;

/** The contract other modules compile against (club::api). Same annotations: cached context. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ClubCatalogTests {

    @Autowired
    ClubCatalog catalog;

    @Autowired
    ClubService clubs;

    @Test
    void otherModulesSeeClubsAsSnapshots() {
        ClubDetails created = clubs.requestCreation(new Actor(UUID.randomUUID(), false),
            new CreateClubCommand("Photo Walks " + UUID.randomUUID().toString().substring(0, 6), null, null, "Arts", List.of()));

        assertThat(catalog.findById(created.id())).hasValueSatisfying(club -> {
            assertThat(club.slug()).isEqualTo(created.slug());
            assertThat(club.status()).isEqualTo(ClubStatus.PENDING_APPROVAL);
            assertThat(club.isActive()).isFalse();
        });
        assertThat(catalog.findBySlug(created.slug())).isPresent();
        assertThat(catalog.findByIds(List.of(created.id(), UUID.randomUUID()))).containsOnlyKeys(created.id());
        assertThatThrownBy(() -> catalog.require(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }
}
