package pt.vclubroom.space.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pt.vclubroom.space.api.ChannelType;

public record CreateChannelRequest(@NotNull ChannelType type, @NotBlank @Size(max = 60) String name) {
}
