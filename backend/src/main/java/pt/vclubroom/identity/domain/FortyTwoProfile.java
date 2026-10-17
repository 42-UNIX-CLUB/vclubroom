package pt.vclubroom.identity.domain;

import java.util.List;

/** Normalised subset of GET https://api.intra.42.fr/v2/me. */
public record FortyTwoProfile(long ftId,
                              String login,
                              String email,
                              String displayName,
                              String avatarUrl,
                              Integer poolYear,
                              String poolMonth,
                              boolean alumni,
                              boolean staff,
                              List<String> campusNames,
                              List<String> cursusSlugs) {
}
