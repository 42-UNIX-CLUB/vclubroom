package pt.vclubroom.membership.web.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import pt.vclubroom.membership.api.ClubPermission;

/** Create: name and permissions required. Update: whatever is present changes. */
public record RoleRequest(@Size(min = 1, max = 40) String name,
                          @Pattern(regexp = "#[0-9a-fA-F]{6}") String color,
                          Set<ClubPermission> permissions) {
}
