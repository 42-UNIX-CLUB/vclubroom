package pt.vclubroom.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pt.vclubroom.identity.domain.FortyTwoProfile;

class FortyTwoProfileMapperTests {

    /** Trimmed GET https://api.intra.42.fr/v2/me response: real field names and types (pool_year is a string). */
    private static final String ME_JSON = """
        {
          "id": 123456,
          "login": "jdoe",
          "email": "jdoe@student.42lisboa.com",
          "displayname": "John Doe",
          "image": {
            "link": "https://cdn.intra.42.fr/users/abc/jdoe.jpg",
            "versions": {"large": "https://cdn.intra.42.fr/users/abc/large_jdoe.jpg",
                         "medium": "https://cdn.intra.42.fr/users/abc/medium_jdoe.jpg"}
          },
          "pool_month": "september",
          "pool_year": "2023",
          "staff?": false,
          "alumni?": true,
          "campus": [{"id": 38, "name": "Lisboa"}],
          "cursus_users": [{"cursus": {"id": 9, "slug": "c-piscine"}}, {"cursus": {"id": 21, "slug": "42cursus"}}]
        }
        """;

    private final FortyTwoProfileMapper mapper = new FortyTwoProfileMapper();

    @Test
    void mapsTheV2MeResponse() throws Exception {
        FortyTwoProfile p = mapper.map(new ObjectMapper().readValue(ME_JSON, new TypeReference<Map<String, Object>>() { }));

        assertThat(p.ftId()).isEqualTo(123456L);
        assertThat(p.login()).isEqualTo("jdoe");
        assertThat(p.email()).isEqualTo("jdoe@student.42lisboa.com");
        assertThat(p.displayName()).isEqualTo("John Doe");
        assertThat(p.avatarUrl()).isEqualTo("https://cdn.intra.42.fr/users/abc/medium_jdoe.jpg");
        assertThat(p.poolYear()).isEqualTo(2023);
        assertThat(p.poolMonth()).isEqualTo("september");
        assertThat(p.alumni()).isTrue();
        assertThat(p.staff()).isFalse();
        assertThat(p.campusNames()).containsExactly("Lisboa");
        assertThat(p.cursusSlugs()).containsExactly("c-piscine", "42cursus");
    }

    @Test
    void toleratesMissingOptionalFields() {
        FortyTwoProfile p = mapper.map(Map.of("id", 7, "login", "minimal", "displayname", " "));

        assertThat(p.displayName()).isEqualTo("minimal");   // blank display name falls back to the login
        assertThat(p.avatarUrl()).isNull();
        assertThat(p.poolYear()).isNull();
        assertThat(p.campusNames()).isEmpty();
        assertThat(p.cursusSlugs()).isEmpty();
    }
}
