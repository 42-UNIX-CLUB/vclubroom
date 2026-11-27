package pt.vclubroom.identity.application;

import java.util.List;

/** What the consent page shows (E20-S05): the app, who made it, the scopes it asks for and those already granted. */
public record ConsentRequestView(String clientId, String name, String description, String homepageUrl, String ownerName,
                                 List<String> scopes, List<String> granted) {
}
