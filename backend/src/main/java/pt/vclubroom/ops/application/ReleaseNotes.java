package pt.vclubroom.ops.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pt.vclubroom.ops.application.Changelog.Change;
import pt.vclubroom.ops.application.Changelog.Release;
import pt.vclubroom.ops.application.Changelog.Section;

/**
 * The changelog (E19-S03), from what git-cliff wrote at the last deploy (deploy/deploy.sh: `--context`, a JSON list of
 * releases with their commits). Read again only when the file changes. git-cliff's commits carry their authors' names
 * and emails: only the version, date, section, scope, description and breaking flag go out. No file: no changelog.
 */
@Service
public class ReleaseNotes {

    private static final Logger log = LoggerFactory.getLogger(ReleaseNotes.class);
    private static final Changelog NONE = new Changelog(null, List.of());
    private static final List<String> ORDER = List.of("Features", "Fixes", "Performance");

    private record Cached(FileTime modified, Changelog changelog) { }

    private final ObjectMapper json;
    private final Path file;
    private volatile Cached cached;

    ReleaseNotes(ObjectMapper json, @Value("${app.release-notes.file:}") String file) {
        this.json = json;
        this.file = file.isBlank() ? null : Path.of(file);
    }

    public Changelog changelog() {
        if (file == null || !Files.isRegularFile(file)) {
            return NONE;
        }
        try {
            FileTime modified = Files.getLastModifiedTime(file);
            Cached c = cached;
            if (c == null || !c.modified().equals(modified)) {
                c = new Cached(modified, parse(json.readTree(file.toFile())));
                cached = c;
            }
            return c.changelog();
        } catch (IOException e) {
            log.warn("Could not read the release notes in {}: {}", file, e.getMessage());
            return NONE;
        }
    }

    static Changelog parse(JsonNode releases) {
        List<Release> out = new ArrayList<>();
        for (JsonNode release : releases) {
            Map<String, List<Change>> sections = new LinkedHashMap<>();
            for (JsonNode commit : release.path("commits")) {
                String group = text(commit, "group");
                if (group != null) {
                    sections.computeIfAbsent(group, g -> new ArrayList<>()).add(new Change(text(commit, "scope"),
                        commit.path("message").asText().lines().findFirst().orElse(""), commit.path("breaking").asBoolean()));
                }
            }
            if (sections.isEmpty()) {
                continue;                                            // nothing users would notice
            }
            List<Section> ordered = sections.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> ORDER.contains(e.getKey()) ? ORDER.indexOf(e.getKey()) : ORDER.size()))
                .map(e -> new Section(e.getKey(), e.getValue())).toList();
            Instant date = release.hasNonNull("timestamp") ? Instant.ofEpochSecond(release.get("timestamp").asLong()) : null;
            out.add(new Release(text(release, "version"), date, ordered));
        }
        return new Changelog(out.stream().map(Release::version).filter(Objects::nonNull).findFirst().orElse(null), out);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
