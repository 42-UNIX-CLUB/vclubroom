package pt.vclubroom.membership.api;

/** Platform-defined capabilities that clubs combine into roles (01-domain-model.md §4). Owners hold all of them. */
public enum ClubPermission {
    CLUB_SETTINGS_MANAGE,
    MEMBER_APPROVE,
    MEMBER_REMOVE,
    ROLE_MANAGE,
    GROUP_MANAGE,
    SPACE_MANAGE,
    SPACE_ACCESS_ALL,
    CONTENT_MODERATE,
    EVENT_MANAGE
}
