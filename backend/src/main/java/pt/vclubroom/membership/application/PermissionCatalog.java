package pt.vclubroom.membership.application;

import java.util.Arrays;
import java.util.List;
import pt.vclubroom.membership.api.ClubPermission;

/** The permission catalogue with the words the role editor shows (GET /api/permissions). */
public final class PermissionCatalog {

    public record PermissionInfo(ClubPermission code, String label, String description) { }

    private PermissionCatalog() {
    }

    public static List<PermissionInfo> entries() {
        return Arrays.stream(ClubPermission.values()).map(p -> switch (p) {
            case CLUB_SETTINGS_MANAGE -> new PermissionInfo(p, "Edit the club", "Change the profile, the images and how people join.");
            case MEMBER_APPROVE -> new PermissionInfo(p, "Approve members", "Accept or decline join requests.");
            case MEMBER_REMOVE -> new PermissionInfo(p, "Remove members", "Remove members who are not owners.");
            case ROLE_MANAGE -> new PermissionInfo(p, "Manage roles", "Create roles and give them to members, within your own permissions.");
            case GROUP_MANAGE -> new PermissionInfo(p, "Manage groups", "Create groups and choose who is in them.");
            case SPACE_MANAGE -> new PermissionInfo(p, "Manage spaces", "Create spaces and channels, and decide who can read and write.");
            case SPACE_ACCESS_ALL -> new PermissionInfo(p, "See every space", "Read and write in every space, whatever its rules.");
            case CONTENT_MODERATE -> new PermissionInfo(p, "Moderate", "Delete and pin posts and comments in any channel.");
            case EVENT_MANAGE -> new PermissionInfo(p, "Manage events", "Schedule, edit and cancel club events.");
        }).toList();
    }
}
