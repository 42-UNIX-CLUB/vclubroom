import type { ClubPermission, MembershipView } from "./api";

/**
 * Whether my membership grants a club permission (owners and admins hold them all). The UI hides what this
 * denies; the backend checks every action again anyway.
 */
export function can(membership: MembershipView | undefined, permission: ClubPermission): boolean {
  return membership?.permissions?.includes(permission) ?? false;
}
