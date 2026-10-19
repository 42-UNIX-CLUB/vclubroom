package pt.vclubroom.club.web.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import pt.vclubroom.club.api.JoinPolicy;
import pt.vclubroom.club.application.UpdateClubCommand;

/** A partial update: only the fields present change. Images are media IDs from an upload (purpose CLUB_AVATAR/BANNER). */
public record UpdateClubRequest(
        @Size(min = 3, max = 60) String name,
        @Size(max = 140) String tagline,
        @Size(max = 5000) String description,
        String category,
        @Size(max = 10) List<@Pattern(regexp = "[a-z0-9-]{2,24}") String> tags,
        UUID avatarMediaId,
        UUID bannerMediaId,
        JoinPolicy joinPolicy) {

    public UpdateClubCommand toCommand() {
        return new UpdateClubCommand(name, tagline, description, category, tags, avatarMediaId, bannerMediaId, joinPolicy);
    }
}
