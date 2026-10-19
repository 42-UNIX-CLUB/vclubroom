package pt.vclubroom.discovery.application;

import java.util.List;

/** The directory's status filter (R-21): active clubs by default, archived ones only on request. */
public enum DirectoryStatus {
    ACTIVE,
    ARCHIVED,
    ALL;

    List<String> statuses() {
        return this == ALL ? List.of("ACTIVE", "ARCHIVED") : List.of(name());
    }
}
