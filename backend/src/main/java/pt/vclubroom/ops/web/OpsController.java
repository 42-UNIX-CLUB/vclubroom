package pt.vclubroom.ops.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.ops.application.OpsReport;
import pt.vclubroom.ops.application.OpsService;

/** SecurityConfig limits /api/admin/** to admins. */
@RestController
class OpsController {

    private final OpsService ops;

    OpsController(OpsService ops) {
        this.ops = ops;
    }

    @GetMapping("/api/admin/ops")
    OpsReport report() {
        return ops.report();
    }
}
