package pt.vclubroom.group.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pt.vclubroom.group.persistence.GroupMemberRepository;
import pt.vclubroom.membership.events.MemberLeft;

/** R-22: leaving a club (or being removed from it) removes the person from that club's groups. Idempotent. */
@Component
class MembershipEndedListener {

    private final GroupMemberRepository members;

    MembershipEndedListener(GroupMemberRepository members) {
        this.members = members;
    }

    @ApplicationModuleListener
    void on(MemberLeft event) {
        members.deleteFromClubGroups(event.clubId(), event.userId());
    }
}
