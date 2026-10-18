package pt.vclubroom.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.identity.application.UserDirectoryService;
import pt.vclubroom.identity.infrastructure.security.LocalPrincipal;
import pt.vclubroom.identity.web.dto.MeResponse;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    record LoginRequest(@NotBlank String username, @NotBlank String password) { }

    private final AuthenticationManager localAuthenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final UserDirectoryService userDirectory;
    private final SessionAuthenticationStrategy sessionStrategy = new ChangeSessionIdAuthenticationStrategy();
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    AuthController(@Qualifier("localAuthenticationManager") AuthenticationManager localAuthenticationManager,
                   SecurityContextRepository securityContextRepository,
                   UserDirectoryService userDirectory) {
        this.localAuthenticationManager = localAuthenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.userDirectory = userDirectory;
    }

    /** Called once by the SPA at startup; the CSRF handler writes the XSRF-TOKEN cookie on the response. */
    @GetMapping("/csrf")
    ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    MeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        Authentication checked = localAuthenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));

        UserDetails details = (UserDetails) checked.getPrincipal();
        MeResponse me = userDirectory.requireMeByLogin(details.getUsername());
        LocalPrincipal principal = new LocalPrincipal(me.id(), me.login(), me.admin());
        Authentication authenticated =
            UsernamePasswordAuthenticationToken.authenticated(principal, null, checked.getAuthorities());

        sessionStrategy.onAuthentication(authenticated, request, response);   // session fixation protection
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authenticated);
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);   // persisted to the Redis session
        userDirectory.recordLogin(me.id());
        return me;
    }

    /** Same answer for unknown user, wrong password and disabled account: no account enumeration. */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail badCredentials(AuthenticationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
        pd.setProperty("code", "INVALID_CREDENTIALS");
        return pd;
    }
}
