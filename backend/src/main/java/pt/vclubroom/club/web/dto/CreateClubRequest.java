package pt.vclubroom.club.web.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pt.vclubroom.club.application.CreateClubCommand;

public record CreateClubRequest(
    @NotBlank @Size(min = 3, max = 60) String name,
    @Size(max = 140) String tagline,
    @Size(max = 5000) String description,
    @NotBlank String category,
    @Size(max = 10) List<@Pattern(regexp = "[a-z0-9-]{2,24}") String> tags) {

    public CreateClubCommand toCommand() {
        return new CreateClubCommand(name, tagline, description, category, tags == null ? List.of() : tags);
    }
}