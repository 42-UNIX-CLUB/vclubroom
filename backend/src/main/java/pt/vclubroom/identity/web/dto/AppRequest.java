package pt.vclubroom.identity.web.dto;

import java.util.List;
import pt.vclubroom.identity.application.AppDetails;

/** confidential is read when registering only: an app keeps its kind. */
public record AppRequest(String name, String description, String homepageUrl, List<String> redirectUris, boolean confidential) {

    public AppDetails details() {
        return new AppDetails(name, description, homepageUrl, redirectUris);
    }
}
