package pt.vclubroom.identity.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pt.vclubroom.identity.application.LocalAccountService;

public record CreateLocalAccountRequest(@NotBlank @Size(max = 30) String username, @NotBlank @Size(max = 100) String displayName,
                                        @Email @Size(max = 320) String email, @NotBlank @Size(max = 128) String temporaryPassword,
                                        boolean admin) {

    public LocalAccountService.NewLocalAccount toAccount() {
        return new LocalAccountService.NewLocalAccount(username, displayName, email, temporaryPassword, admin);
    }
}
