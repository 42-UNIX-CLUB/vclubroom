package pt.vclubroom.membership.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignOwnerRequest(@NotNull UUID userId) {
}
