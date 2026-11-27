package pt.vclubroom.support.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.support.domain.Ticket;
import pt.vclubroom.support.domain.TicketType;

/** What reporters send outside the fence (page, browser, name) can't add links, images or mentions to the team's issue. */
class IssueTextTests {

    @Test
    void contextFieldsCantAddMarkdownToTheTeamsIssue() {
        String page = "/c/chess?tab=posts\n\n[Sign in again here](https://evil.example/login) @support-lead";
        String agent = "Firefox\n\n![](https://evil.example/pixel.png)";
        Ticket t = Ticket.open(7, UUID.randomUUID(), TicketType.BUG, "Broken", "It broke", page, agent, Instant.now());
        UserSummary reporter = new UserSummary(UUID.randomUUID(), "ada", "Ada [admin](https://evil.example) @support-lead", null);

        String rendered = IssueText.body(t, reporter, "https://app.example");
        String beforeFence = rendered.substring(0, rendered.indexOf("~~~text"));

        assertThat(beforeFence).contains("Page: https://app.example/c/chess?tab=posts%0A%0A%5BSign%20in");   // still the page
        assertThat(beforeFence.lines().filter(l -> !l.isBlank())).hasSize(4);                          // no injected lines
        assertThat(beforeFence).doesNotContain("](https://evil.example/login)").doesNotContain("\n![");
        assertThat(beforeFence).contains("`Ada [admin](https://evil.example) @support-lead`");          // a code span
        assertThat(IssueText.reply(reporter, "thanks")).startsWith("`Ada [admin](https://evil.example) @support-lead` replied");
    }
}
