package pt.vclubroom.club.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import pt.vclubroom.shared.error.InvalidRequestException;

class SlugTests {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Chess Club                  | chess-club",
        "Échecs & Go!                | echecs-go",
        "  42 Robotics -- Lisbon  ¿? | 42-robotics-lisbon",
        "Go                          | go-club",          // too short: "-club" suffix
        "!!                          | club",             // nothing usable left
    })
    void derivesAUrlSafeSlugFromTheName(String name, String expected) {
        assertThat(Slug.fromName(name).value()).isEqualTo(expected);
    }

    @Test
    void cutsLongNamesAtFortyCharactersWithoutATrailingHyphen() {
        String slug = Slug.fromName("The Very Long Name Of A Club About Everything Under The Sun").value();
        assertThat(slug).hasSizeLessThanOrEqualTo(40).doesNotEndWith("-");
    }

    @Test
    void addsANumberToMakeASlugUnique() {
        assertThat(Slug.fromName("Chess Club").withSuffix(2).value()).isEqualTo("chess-club-2");
        assertThat(new Slug("a".repeat(40)).withSuffix(12).value()).hasSize(39).endsWith("-12");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "-chess", "chess-", "Chess", "chess club", "ç-club"})
    void rejectsInvalidSlugs(String value) {
        assertThatThrownBy(() -> new Slug(value))
            .isInstanceOfSatisfying(InvalidRequestException.class, e -> assertThat(e.code()).isEqualTo("INVALID_SLUG"));
    }
}