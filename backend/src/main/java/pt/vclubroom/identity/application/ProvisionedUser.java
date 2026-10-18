package pt.vclubroom.identity.application;

import java.util.UUID;

public record ProvisionedUser(UUID id, String login, boolean admin, boolean active) {
}
