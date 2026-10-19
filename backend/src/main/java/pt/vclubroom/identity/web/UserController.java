package pt.vclubroom.identity.web;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.identity.application.UserDirectoryService;
import pt.vclubroom.shared.error.NotFoundException;

/** Finding people: only the public summary (login, name, avatar), never emails. /api/** requires a login. */
@RestController
@RequestMapping("/api/users")
class UserController {

    private final UserDirectoryService directory;

    UserController(UserDirectoryService directory) {
        this.directory = directory;
    }

    @GetMapping
    List<UserSummary> search(@RequestParam(name = "query", defaultValue = "") String query,
                             @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return directory.search(query, limit);
    }

    @GetMapping("/{userId}")
    UserSummary user(@PathVariable("userId") UUID userId) {
        return directory.findById(userId).orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
    }
}
