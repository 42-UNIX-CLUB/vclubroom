package pt.vclubroom.ops.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.ops.application.PublicStatus;
import pt.vclubroom.ops.application.StatusService;

/** Public (E19-S02): SecurityConfig lets anyone call /api/public/**. */
@RestController
class PublicStatusController {

    private final StatusService status;

    PublicStatusController(StatusService status) {
        this.status = status;
    }

    @GetMapping("/api/public/status")
    PublicStatus status() {
        return status.status();
    }
}
