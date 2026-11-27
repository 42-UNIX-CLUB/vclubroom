package pt.vclubroom.support.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * app.support.forgejo.*: where the tracker is, the bot's token, the repository tickets go to, and the development
 * repository whose issues tickets can be linked to (E18-S03), as "owner/name".
 */
@ConfigurationProperties("app.support.forgejo")
public record ForgejoProperties(@DefaultValue("http://localhost:3000") String url, String token,
                                @DefaultValue("vclubroom/support") String repository,
                                @DefaultValue("vclubroom/vclubroom") String development) {
}
