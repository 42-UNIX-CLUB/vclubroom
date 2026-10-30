package pt.vclubroom.support.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TeamRepliesTests {

    @Test
    void onlyACommentThatStartsWithReplyReachesTheReporter() {
        assertThat(TeamReplies.forReporter("/reply Thanks! It's fixed.")).contains("Thanks! It's fixed.");
        assertThat(TeamReplies.forReporter("  /REPLY\nLine one\nLine two")).contains("Line one\nLine two");
        assertThat(TeamReplies.forReporter("Reproduced on Firefox, see /reply later")).isEmpty();
        assertThat(TeamReplies.forReporter("/replying soon")).isEmpty();
        assertThat(TeamReplies.forReporter("/reply   ")).isEmpty();
        assertThat(TeamReplies.forReporter(null)).isEmpty();
    }
}
