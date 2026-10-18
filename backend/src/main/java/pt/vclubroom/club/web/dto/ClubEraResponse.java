package pt.vclubroom.club.web.dto;

import java.time.Instant;

import pt.vclubroom.club.application.ClubDetails;

public record ClubEraResponse(int number, Instant startedAt, String startReason, Instant endedAt, String endReason) {

    public static ClubEraResponse from(ClubDetails.Era e) {
        return new ClubEraResponse(e.number(), e.startedAt(), e.startReason(), e.endedAt(), e.endReason());
    }
}