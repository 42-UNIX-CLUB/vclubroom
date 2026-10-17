package pt.vclubroom.shared.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The error contract every module relies on: RFC 9457 body, HTTP status per exception type, stable "code". */
class GlobalExceptionHandlerTests {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    @ParameterizedTest
    @CsvSource({
        "not-found,       404, CLUB_NOT_FOUND",
        "forbidden,       403, NOT_A_MEMBER",
        "conflict,        409, ALREADY_MEMBER",
        "business-rule,   422, LAST_OWNER",
        "invalid-request, 400, INVALID_CURSOR",
        "duplicate,       409, DUPLICATE"
    })
    void mapsExceptionsToProblemDetailsWithStableCode(String kind, int httpStatus, String code) throws Exception {
        mvc.perform(get("/throw/{kind}", kind))
            .andExpect(status().is(httpStatus))
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(httpStatus))
            .andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void validationErrorsListTheRejectedFields() throws Exception {
        mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/throw/{kind}")
        void fail(@PathVariable("kind") String kind) {
            throw switch (kind) {
                case "not-found" -> new NotFoundException("CLUB_NOT_FOUND", "No club with this slug.");
                case "forbidden" -> new ForbiddenException("NOT_A_MEMBER", "Only members can post here.");
                case "conflict" -> new ConflictException("ALREADY_MEMBER", "You are already a member.");
                case "business-rule" -> new BusinessRuleException("LAST_OWNER", "A club keeps at least one owner.");
                case "invalid-request" -> new InvalidRequestException("INVALID_CURSOR", "The cursor is invalid.");
                case "duplicate" -> new DataIntegrityViolationException("ux_memberships_active");
                default -> new IllegalArgumentException(kind);
            };
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody NameRequest request) {
        }

        record NameRequest(@NotBlank String name) {
        }
    }
}
