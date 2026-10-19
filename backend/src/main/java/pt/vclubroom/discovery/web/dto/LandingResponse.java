package pt.vclubroom.discovery.web.dto;

import java.util.List;
import pt.vclubroom.discovery.application.Landing;

public record LandingResponse(Landing.Stats stats, List<ClubCardResponse> mostActive, List<ClubCardResponse> newest,
                              List<Landing.CategoryCount> categories) {

    public static LandingResponse from(Landing l) {
        return new LandingResponse(l.stats(), l.mostActive().stream().map(ClubCardResponse::from).toList(),
            l.newest().stream().map(ClubCardResponse::from).toList(), l.categories());
    }
}
