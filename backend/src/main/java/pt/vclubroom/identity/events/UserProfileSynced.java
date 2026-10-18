package pt.vclubroom.identity.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Published at every 42 login with the facts the group module needs to sync campus groups. */
public record UserProfileSynced(UUID userId,
                                String login,
                                Integer poolYear,
                                String poolMonth,
                                List<String> campusNames,
                                List<String> cursusSlugs,
                                boolean alumni,
                                Instant occurredAt) {
}
