package pt.vclubroom.membership.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.membership.api.ClubPermission;
import pt.vclubroom.membership.domain.ClubRole;
import pt.vclubroom.membership.domain.Membership;
import pt.vclubroom.membership.domain.SystemRole;

/** The documented queries against real PostgreSQL (text[] permissions, the role join table). Rolled back after each test. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class MembershipRepositoryTests {

    @Autowired
    MembershipRepository memberships;

    @Autowired
    ClubRoleRepository roles;

    private final Instant now = Instant.parse("2026-10-18T20:00:00Z");
    private final UUID club = UUID.randomUUID();
    private final UUID founder = UUID.randomUUID();
    private final UUID organiser = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID formerMember = UUID.randomUUID();
    private ClubRole owner;

    @BeforeEach
    void aClubWithFourPeople() {
        owner = roles.save(ClubRole.systemOwner(club, now));
        ClubRole memberRole = roles.save(ClubRole.systemMember(club, now));
        ClubRole events = roles.save(ClubRole.custom(club, "Events", "#22c55e", Set.of(ClubPermission.EVENT_MANAGE), 10, now));

        Membership f = Membership.start(club, founder, memberRole.id(), now);
        f.grantRole(owner.id());
        Membership o = Membership.start(club, organiser, memberRole.id(), now);
        o.grantRole(events.id());
        Membership left = Membership.start(club, formerMember, memberRole.id(), now);
        left.grantRole(owner.id());
        left.leave(now);
        memberships.saveAll(Set.of(f, o, Membership.start(club, member, memberRole.id(), now), left));
        memberships.flush();                                  // the native query below reads the tables, not the session
    }

    @Test
    void heldRolesAndOwnersCountOnlyActiveMemberships() {
        assertThat(roles.findHeldRoles(club, founder)).extracting(ClubRole::systemRole)
            .containsExactlyInAnyOrder(SystemRole.OWNER, SystemRole.MEMBER);
        assertThat(roles.findHeldRoles(club, formerMember)).isEmpty();
        assertThat(memberships.countActiveWithRole(club, owner.id())).isEqualTo(1);
        assertThat(memberships.findActiveWithRole(club, owner.id())).extracting(Membership::userId).containsExactly(founder);
    }

    @Test
    void holdersOfAPermissionIncludeOwners() {
        assertThat(roles.findHolderIds(club, ClubPermission.EVENT_MANAGE.name())).containsExactlyInAnyOrder(founder, organiser);
        assertThat(roles.findHolderIds(club, ClubPermission.MEMBER_REMOVE.name())).containsExactly(founder);
    }

    @Test
    void membersAndClubsOfActiveMembershipsOnly() {
        assertThat(memberships.existsActive(club, member)).isTrue();
        assertThat(memberships.existsActive(club, formerMember)).isFalse();
        assertThat(memberships.findActiveUserIds(club)).containsExactlyInAnyOrder(founder, organiser, member);
        assertThat(memberships.findActiveClubIds(formerMember)).isEmpty();
        assertThat(roles.findSystemRoleForUpdate(club, SystemRole.OWNER)).hasValue(owner);   // SELECT … FOR UPDATE
    }
}
