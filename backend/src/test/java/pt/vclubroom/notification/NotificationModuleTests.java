package pt.vclubroom.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.calendar.api.RsvpQuery;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.forum.events.PostPublished;
import pt.vclubroom.identity.events.AccessTokenExpiring;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.realtime.api.RealtimePublisher;
import pt.vclubroom.space.api.SpaceAudience;
import pt.vclubroom.support.events.TicketStatusChanged;
import pt.vclubroom.support.events.TicketSubmitted;

/** The notification module on its own (E12-S01): events in, rows and pushes out; the other modules are mocked. */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class NotificationModuleTests {

    @MockitoBean
    SpaceAudience audience;

    @MockitoBean
    UserDirectory users;

    @MockitoBean
    ClubAuthorization authorization;

    @MockitoBean
    ClubCatalog clubs;

    @MockitoBean
    RealtimePublisher realtime;

    @MockitoBean
    RsvpQuery rsvps;

    @Autowired
    JdbcTemplate jdbc;

    private long rowsFor(UUID recipient) {
        return jdbc.queryForObject("select count(*) from notification.notifications where recipient_id = ?", Long.class, recipient);
    }

    @Test
    void aNewPostReachesTheReadersOnceEvenWhenTheEventComesTwice(Scenario scenario) {
        UUID author = UUID.randomUUID();
        UUID ada = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        given(audience.clubMembersWithReadAccess(spaceId)).willReturn(Set.of(author, ada, bob));
        PostPublished published = new PostPublished(UUID.randomUUID(), UUID.randomUUID(), spaceId, UUID.randomUUID(), author, 1,
            "Friday blitz", "Bring a clock", Instant.now());

        scenario.publish(published).andWaitForStateChange(() -> rowsFor(ada) + rowsFor(bob), total -> total == 2);
        scenario.publish(published).andWaitForStateChange(() -> rowsFor(ada) + rowsFor(bob), total -> total == 2);   // redelivered

        assertThat(rowsFor(author)).isZero();                                       // never about your own post
        verify(realtime, times(1)).toUser(eq(ada), eq("/queue/notifications"), eq("notification.created"), any());
        verify(realtime, times(1)).toUser(eq(bob), eq("/queue/notifications"), eq("notification.created"), any());
    }

    @Test
    void supportTicketsAreOnlyEverToldToTheirReporter(Scenario scenario) {
        UUID ada = UUID.randomUUID();
        UUID ticket = UUID.randomUUID();

        scenario.publish(new TicketStatusChanged(ticket, 17, "T-17", ada, "Login button does nothing", "WAITING_ON_REPORTER",
                "Waiting for you", false, Instant.now()))
            .andWaitForStateChange(() -> rowsFor(ada), count -> count == 1);

        assertThat(jdbc.queryForMap("select type, title, body, link, in_app, email_requested from notification.notifications where recipient_id = ?", ada))
            .containsEntry("type", "SUPPORT_TICKET_UPDATED")
            .containsEntry("title", "T-17 is now waiting for you")
            .containsEntry("body", "The team needs your answer to go on with “Login button does nothing”.")
            .containsEntry("link", "/support/17")
            .containsEntry("in_app", true)
            .containsEntry("email_requested", false);                   // status changes: in-app only by default
        scenario.publish(new TicketSubmitted(ticket, 17, "T-17", ada, "Login button does nothing", Instant.now()))
            .andWaitForStateChange(() -> rowsFor(ada), count -> count == 2);
        assertThat(jdbc.queryForObject("select email_requested from notification.notifications where recipient_id = ? and type = 'SUPPORT_TICKET_RECEIVED'",
            Boolean.class, ada)).isTrue();
    }

    @Test
    void aTokenAboutToExpireIsToldToItsOwnerInTheAppAndByEmail(Scenario scenario) {
        UUID ada = UUID.randomUUID();
        UUID token = UUID.randomUUID();

        scenario.publish(new AccessTokenExpiring(token, ada, "Backup script", Instant.parse("2026-11-06T10:00:00Z")))
            .andWaitForStateChange(() -> rowsFor(ada), count -> count == 1);

        assertThat(jdbc.queryForMap("select type, title, link, in_app, email_requested from notification.notifications where recipient_id = ?", ada))
            .containsEntry("type", "ACCESS_TOKEN_EXPIRING")
            .containsEntry("title", "Your token “Backup script” expires on 6 Nov 2026")
            .containsEntry("link", "/settings#tokens")
            .containsEntry("in_app", true)
            .containsEntry("email_requested", true);
    }

    @Test
    void aMutedTypeIsSkipped(Scenario scenario) {
        UUID muted = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        jdbc.update("insert into notification.preferences (user_id, type, in_app, email) values (?, 'NEW_POST', false, false)", muted);
        given(audience.clubMembersWithReadAccess(spaceId)).willReturn(Set.of(muted, other));

        scenario.publish(new PostPublished(UUID.randomUUID(), UUID.randomUUID(), spaceId, UUID.randomUUID(), UUID.randomUUID(), 1,
                null, "Hello", Instant.now()))
            .andWaitForStateChange(() -> rowsFor(other), count -> count == 1);
        assertThat(rowsFor(muted)).isZero();
    }
}
