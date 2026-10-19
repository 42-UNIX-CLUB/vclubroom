package pt.vclubroom.club.application;

import java.util.List;
import java.util.UUID;
import pt.vclubroom.club.api.JoinPolicy;

/** What changes: null means "leave as it is". */
public record UpdateClubCommand(String name, String tagline, String description, String category, List<String> tags,
                                UUID avatarMediaId, UUID bannerMediaId, JoinPolicy joinPolicy) {
}
