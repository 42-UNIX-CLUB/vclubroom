package pt.vclubroom.club.application;

import java.util.List;

public record CreateClubCommand(String name, String tagline, String description, String category, List<String> tags) {
}