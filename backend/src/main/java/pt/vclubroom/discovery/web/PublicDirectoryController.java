package pt.vclubroom.discovery.web;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.discovery.application.DirectoryQueryService;

/** Anonymous endpoints for the landing page and the directory (/api/public/** is permitAll in SecurityConfig). */
@RestController
@RequestMapping("/api/public")
class PublicDirectoryController {

    private final DirectoryQueryService directory;

    PublicDirectoryController(DirectoryQueryService directory) {
        this.directory = directory;
    }

    /** The fixed category list (R-20): create-club form and directory filter. */
    @GetMapping("/categories")
    List<String> categories() {
        return directory.categories();
    }
}
