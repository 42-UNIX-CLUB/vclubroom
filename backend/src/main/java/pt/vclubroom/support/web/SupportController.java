package pt.vclubroom.support.web;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.shared.security.AuthenticatedUser;
import pt.vclubroom.support.application.SupportService;
import pt.vclubroom.support.application.TicketView;
import pt.vclubroom.support.web.dto.CreateTicketRequest;

/** My tickets (E17-S02). /api/** requires a logged-in user (SecurityConfig). */
@RestController
@RequestMapping("/api/support/tickets")
class SupportController {

    private final SupportService support;

    SupportController(SupportService support) {
        this.support = support;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TicketView submit(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody CreateTicketRequest request) {
        return support.submit(me.actor(), request.toCommand());
    }

    @GetMapping
    List<TicketView> mine(@AuthenticationPrincipal AuthenticatedUser me) {
        return support.mine(me.actor());
    }

    @GetMapping("/{number}")
    TicketView get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("number") long number) {
        return support.get(me.actor(), number);
    }
}
