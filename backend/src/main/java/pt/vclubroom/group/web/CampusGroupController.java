package pt.vclubroom.group.web;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.group.application.CampusGroupService;
import pt.vclubroom.group.application.CampusGroupView;

/** Campus groups for everyone (group.md §7): the access-rule picker offers them next to the club's own. */
@RestController
class CampusGroupController {

    private final CampusGroupService groups;

    CampusGroupController(CampusGroupService groups) {
        this.groups = groups;
    }

    @GetMapping("/api/campus-groups")
    List<CampusGroupView> list() {
        return groups.list();
    }
}
