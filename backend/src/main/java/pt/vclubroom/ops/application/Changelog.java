package pt.vclubroom.ops.application;

import java.time.Instant;
import java.util.List;

/**
 * GET /api/public/changelog (E19-S03): the releases, newest first, the first one without a version being what isn't
 * released yet; current is the latest release. Only what users notice (features, fixes, performance), and no authors.
 */
public record Changelog(String current, List<Release> releases) {

    /** version: "v1.1.0", or null for what is merged but not released yet (date null too). */
    public record Release(String version, Instant date, List<Section> sections) { }

    /** title: "Features", "Fixes" or "Performance" (cliff.toml). */
    public record Section(String title, List<Change> changes) { }

    /** scope: the backlog ID ("E17-S02"), if any. */
    public record Change(String scope, String description, boolean breaking) { }
}
