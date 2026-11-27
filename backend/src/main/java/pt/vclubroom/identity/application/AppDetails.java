package pt.vclubroom.identity.application;

import java.util.List;

/** What an app's owner says about it (E20-S04): shown to the people asked to let it in. */
public record AppDetails(String name, String description, String homepageUrl, List<String> redirectUris) {
}
