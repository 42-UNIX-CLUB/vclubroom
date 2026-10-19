package pt.vclubroom.discovery.web;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.discovery.application.DirectoryQuery;
import pt.vclubroom.discovery.application.DirectoryQueryService;
import pt.vclubroom.discovery.application.DirectoryStatus;
import pt.vclubroom.discovery.web.dto.ClubCardResponse;
import pt.vclubroom.discovery.web.dto.ClubProfileResponse;
import pt.vclubroom.discovery.web.dto.LandingResponse;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.web.PageResponse;

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

    @GetMapping("/landing")
    LandingResponse landing() {
        return LandingResponse.from(directory.landing());
    }

    /** An unknown status (not ACTIVE, ARCHIVED or ALL) is a 400; an unknown sort falls back to "active". */
    @GetMapping("/clubs")
    PageResponse<ClubCardResponse> clubs(@RequestParam(name = "q", required = false) String q,
                                         @RequestParam(name = "category", required = false) String category,
                                         @RequestParam(name = "tag", required = false) String tag,
                                         @RequestParam(name = "status", defaultValue = "ACTIVE") DirectoryStatus status,
                                         @RequestParam(name = "sort", defaultValue = "active") String sort,
                                         @RequestParam(name = "page", defaultValue = "0") int page,
                                         @RequestParam(name = "size", defaultValue = "20") int size) {
        var result = directory.search(new DirectoryQuery(q, category, tag, status, sort, page, size));
        return new PageResponse<>(result.items().stream().map(ClubCardResponse::from).toList(),
            result.page(), result.size(), result.totalElements());
    }

    @GetMapping("/clubs/{slug}")
    ClubProfileResponse club(@PathVariable("slug") String slug) {
        return directory.profile(slug).map(ClubProfileResponse::from)
            .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Club not found"));
    }
}
