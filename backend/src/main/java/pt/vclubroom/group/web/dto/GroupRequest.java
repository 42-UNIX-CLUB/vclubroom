package pt.vclubroom.group.web.dto;

import jakarta.validation.constraints.Size;

/** Create: a name is required. Update: whatever is present changes. */
public record GroupRequest(@Size(min = 1, max = 80) String name, @Size(max = 500) String description) {
}
