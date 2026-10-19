package pt.vclubroom.discovery.application;

import java.util.List;
import pt.vclubroom.discovery.persistence.DirectorySearchRepository.ClubCardRow;

/** Everything the public landing page shows, in one response. */
public record Landing(Stats stats, List<ClubCardRow> mostActive, List<ClubCardRow> newest, List<CategoryCount> categories) {

    public record Stats(long activeClubs, long archivedClubs, long memberships) { }

    public record CategoryCount(String name, long count) { }
}
