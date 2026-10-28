package pt.vclubroom.space.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSpaceRequest(@NotBlank @Size(max = 60) String name, @Size(max = 500) String description,
                                 @NotNull @Valid RuleRequest readRule, @NotNull @Valid RuleRequest writeRule) {
}
