package pt.vclubroom.identity.application;

import java.time.Instant;
import java.util.List;

/** An OAuth app as its owner sees it (E20-S04). confidential: it has a client secret, kept on a server. */
public record AppView(String id, String clientId, String name, String description, String homepageUrl,
                      List<String> redirectUris, boolean confidential, Instant createdAt) {
}
