package pt.vclubroom.ops.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.ops.application.Changelog;
import pt.vclubroom.ops.application.ReleaseNotes;

/** Public (E19-S03): SecurityConfig lets anyone call /api/public/**. */
@RestController
class ChangelogController {

    private final ReleaseNotes releaseNotes;

    ChangelogController(ReleaseNotes releaseNotes) {
        this.releaseNotes = releaseNotes;
    }

    @GetMapping("/api/public/changelog")
    Changelog changelog() {
        return releaseNotes.changelog();
    }
}
