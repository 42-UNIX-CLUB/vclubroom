package pt.vclubroom.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pt.vclubroom.shared.error.InvalidRequestException;

class TimeCursorTests {

    @Test
    void roundTripsThroughAnOpaqueUrlSafeToken() {
        TimeCursor cursor = new TimeCursor(
            Instant.parse("2026-10-19T09:30:00.123456Z"), UUID.fromString("0b8a3c1e-5f2d-4a7b-9c6e-1d2f3a4b5c6d"));

        String token = cursor.encode();

        assertThat(token).matches("[A-Za-z0-9_-]+");   // safe in a query string, no padding
        assertThat(TimeCursor.decode(token)).isEqualTo(cursor);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not-base64!",                  // not Base64url at all
        "",                             // empty
        "bm8tc2VwYXJhdG9y",             // "no-separator"
        "MjAyNi0xMC0xOVQwOTozMDowMFo"   // "2026-10-19T09:30:00Z" without the id part
    })
    void rejectsTamperedTokensAsInvalidCursor(String token) {
        assertThatThrownBy(() -> TimeCursor.decode(token))
            .isInstanceOfSatisfying(InvalidRequestException.class,
                e -> assertThat(e.code()).isEqualTo("INVALID_CURSOR"));
    }
}
