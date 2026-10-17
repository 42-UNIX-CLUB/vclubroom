package pt.vclubroom.identity.api;

import java.util.UUID;

/** Contact data for notifications. Only the notification module should need it. */
public record UserContact(UUID id, String email, String displayName, boolean active) {
}
