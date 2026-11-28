package pt.vclubroom.identity.infrastructure.oauth;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.util.UUID;
import pt.vclubroom.identity.infrastructure.security.ClubroomOAuth2User;
import pt.vclubroom.identity.infrastructure.security.LocalPrincipal;

/**
 * The authorization server keeps who signed in to an app, as JSON (E20-S05). Spring Security only reads back classes
 * it was told about: these mixins tell it about our two principals, the 42 one and the local one.
 */
final class PrincipalMixins extends SimpleModule {

    PrincipalMixins() {
        super(PrincipalMixins.class.getName());
        setMixInAnnotation(ClubroomOAuth2User.class, FortyTwoUserMixin.class);
        setMixInAnnotation(LocalPrincipal.class, LocalPrincipalMixin.class);
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    abstract static class FortyTwoUserMixin {

        @JsonCreator
        FortyTwoUserMixin(@JsonProperty("userId") UUID userId, @JsonProperty("login") String login,
                          @JsonProperty("admin") boolean admin) {
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    abstract static class LocalPrincipalMixin {

        @JsonCreator
        LocalPrincipalMixin(@JsonProperty("userId") UUID userId, @JsonProperty("login") String login,
                            @JsonProperty("admin") boolean admin) {
        }
    }
}
