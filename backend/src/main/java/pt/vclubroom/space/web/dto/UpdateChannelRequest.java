package pt.vclubroom.space.web.dto;

import jakarta.validation.constraints.Size;

/** Whatever is present changes. position: the channel's place in its space (0 = first). */
public record UpdateChannelRequest(@Size(min = 1, max = 60) String name, Integer position) {
}
