package pt.vclubroom.identity.application;

import java.util.List;

/** An app someone let in (E20-S05), with the scopes they consented to. */
public record AuthorizedAppView(String appId, String name, String homepageUrl, List<String> scopes) {
}
