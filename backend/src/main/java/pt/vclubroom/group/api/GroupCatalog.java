package pt.vclubroom.group.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GroupCatalog {

    /**
     * BR-12: every group must be a CLUB group of this club or a CAMPUS group.
     * @throws pt.vclubroom.shared.error.BusinessRuleException GROUP_NOT_USABLE
     */
    void assertUsableInClub(UUID clubId, Collection<UUID> groupIds);

    List<GroupSummary> findByIds(Collection<UUID> groupIds);
}
