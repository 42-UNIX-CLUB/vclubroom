package pt.vclubroom.space.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import pt.vclubroom.space.application.SpaceService;
import pt.vclubroom.space.domain.AccessLevel;

/** Who may read, or post: PUBLIC, CLUB_MEMBERS, or GROUPS with the groups' ids. */
public record RuleRequest(@NotNull AccessLevel level, List<UUID> groupIds) {

    public SpaceService.Rule toRule() {
        return new SpaceService.Rule(level, groupIds);
    }
}
