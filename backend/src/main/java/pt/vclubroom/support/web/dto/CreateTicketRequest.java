package pt.vclubroom.support.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pt.vclubroom.support.application.NewTicket;
import pt.vclubroom.support.domain.TicketType;

/** pageUrl and userAgent are optional context; the service keeps them only where they help (SupportService). */
public record CreateTicketRequest(
    @NotNull TicketType type,
    @NotBlank @Size(max = 120) String title,
    @NotBlank @Size(max = 5000) String description,
    @Size(max = 2000) String pageUrl,
    @Size(max = 1000) String userAgent) {

    public NewTicket toCommand() {
        return new NewTicket(type, title, description, pageUrl, userAgent);
    }
}
