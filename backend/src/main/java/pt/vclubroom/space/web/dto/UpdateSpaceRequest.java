package pt.vclubroom.space.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** Whatever is present changes. position: the space's place in the club's list (0 = first). */
public record UpdateSpaceRequest(@Size(min = 1, max = 60) String name, @Size(max = 500) String description, Integer position,
                                 @Valid RuleRequest readRule, @Valid RuleRequest writeRule) {
}
