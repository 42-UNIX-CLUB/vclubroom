package pt.vclubroom.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import pt.vclubroom.TestcontainersConfiguration;

/**
 * Every API endpoint is either public, kept from tokens on purpose, or in a scope's area (E20-S01): a new endpoint
 * fails here until someone decides which.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EveryEndpointHasAScopeIntegrationTests {

    /** The endpoints tokens must not reach. */
    private static final Set<String> NOT_FOR_TOKENS_PREFIXES = Set.of("/api/admin/", "/api/auth/", "/api/me/password",
        "/api/me/tokens", "/api/me/apps", "/api/me/authorized-apps", "/api/oauth/");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Test
    void everyEndpointIsPublicOrKeptFromTokensOrInAnArea() {
        List<String> withoutScope = new ArrayList<>();
        mappings.getHandlerMethods().keySet().forEach(info -> {
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String path : info.getPatternValues()) {
                if (!path.startsWith("/api/") || path.startsWith("/api/public/")
                    || NOT_FOR_TOKENS_PREFIXES.stream().anyMatch(path::startsWith)) {
                    continue;
                }
                for (RequestMethod method : methods.isEmpty() ? Set.of(RequestMethod.GET, RequestMethod.POST) : methods) {
                    if (ApiScopes.required(method.name(), path).isEmpty()) {
                        withoutScope.add(method + " " + path);
                    }
                }
            }
        });
        assertThat(withoutScope).isEmpty();
    }
}
