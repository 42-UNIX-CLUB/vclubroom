package pt.vclubroom.discovery.application;

/** sort: active (default) | new | members | name. Every filter is optional. */
public record DirectoryQuery(String q, String category, String tag, DirectoryStatus status, String sort,
                             int page, int size) {
}
