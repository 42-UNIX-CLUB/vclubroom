import { can, type MembershipView } from "@/features/membership";

/** Club settings tabs (08-frontend §4): each one needs its permission, Ownership is for owners. */
const SETTINGS_TABS: { path: string; label: string; visible: (membership: MembershipView | undefined) => boolean }[] = [
  { path: "profile", label: "Profile", visible: (m) => can(m, "CLUB_SETTINGS_MANAGE") },
  { path: "requests", label: "Join requests", visible: (m) => can(m, "MEMBER_APPROVE") },
  { path: "roles", label: "Roles", visible: (m) => can(m, "ROLE_MANAGE") },
  { path: "groups", label: "Groups", visible: (m) => can(m, "GROUP_MANAGE") },
  { path: "spaces", label: "Spaces", visible: (m) => can(m, "SPACE_MANAGE") },
  { path: "ownership", label: "Ownership", visible: (m) => !!m?.owner },
];

export function visibleSettingsTabs(membership: MembershipView | undefined) {
  return SETTINGS_TABS.filter((tab) => tab.visible(membership));
}
