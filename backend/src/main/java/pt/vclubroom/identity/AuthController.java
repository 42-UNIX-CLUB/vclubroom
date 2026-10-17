package pt.vclubroom.identity;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    /** Called once by the SPA at startup; the CSRF handler writes the XSRF-TOKEN cookie on the response. */
    @GetMapping("/csrf")
    ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }
}
