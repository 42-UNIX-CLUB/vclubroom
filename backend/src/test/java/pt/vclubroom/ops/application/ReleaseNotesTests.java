package pt.vclubroom.ops.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pt.vclubroom.ops.application.Changelog.Change;
import pt.vclubroom.ops.application.Changelog.Release;
import pt.vclubroom.ops.application.Changelog.Section;

/** The changelog from git-cliff's context (E19-S03), as `git-cliff --context` writes it, trimmed to what matters here. */
class ReleaseNotesTests {

    private static final String CONTEXT = """
        [
          {"version": null, "timestamp": null, "commits": [
            {"message": "linked work on the ticket page", "group": "Features", "scope": "E18-S03-T3", "breaking": false,
             "author": {"name": "Ada Lovelace", "email": "ada@example.test"}},
            {"message": "keep the last message\\n\\nwith a body", "group": "Fixes", "scope": null, "breaking": false},
            {"message": "new ticket API", "group": "Features", "scope": "E17-S02", "breaking": true}
          ]},
          {"version": "v1.0.0", "timestamp": 1792411200, "commits": [
            {"message": "faster feed", "group": "Performance", "scope": "E09-S05", "breaking": false},
            {"message": "request club creation", "group": "Features", "scope": "E04-S01", "breaking": false}
          ]},
          {"version": "v0.9.0", "timestamp": 1792000000, "commits": []}
        ]""";

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void releasesComeNewestFirstWithTheirSectionsInOrder() throws Exception {
        Changelog changelog = ReleaseNotes.parse(json.readTree(CONTEXT));

        assertThat(changelog.current()).isEqualTo("v1.0.0");
        assertThat(changelog.releases()).extracting(Release::version).containsExactly(null, "v1.0.0");   // nothing to show in v0.9.0
        assertThat(changelog.releases().getFirst().sections()).containsExactly(
            new Section("Features", List.of(new Change("E18-S03-T3", "linked work on the ticket page", false),
                new Change("E17-S02", "new ticket API", true))),
            new Section("Fixes", List.of(new Change(null, "keep the last message", false))));
        assertThat(changelog.releases().get(1).date()).isEqualTo(Instant.ofEpochSecond(1792411200));
        assertThat(changelog.releases().get(1).sections()).extracting(Section::title).containsExactly("Features", "Performance");
        assertThat(json.findAndRegisterModules().writeValueAsString(changelog)).doesNotContain("ada@example.test", "Ada Lovelace");
    }

    @Test
    void theFileIsReadAgainOnlyWhenItChanges(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("changelog.json");
        ReleaseNotes notes = new ReleaseNotes(json, file.toString());
        assertThat(notes.changelog()).isEqualTo(new Changelog(null, List.of()));     // not written yet

        Files.writeString(file, CONTEXT);
        assertThat(notes.changelog().current()).isEqualTo("v1.0.0");
        Files.writeString(file, "[{\"version\": \"v1.1.0\", \"timestamp\": 1793000000, \"commits\": "
            + "[{\"message\": \"a status page\", \"group\": \"Features\", \"scope\": \"E19-S02\", \"breaking\": false}]}]");
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().plusSeconds(5)));
        assertThat(notes.changelog().current()).isEqualTo("v1.1.0");
        assertThat(new ReleaseNotes(json, "").changelog().releases()).isEmpty();      // not configured
    }
}
