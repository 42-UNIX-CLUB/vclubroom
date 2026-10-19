package pt.vclubroom.forum.application;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.forum.reactions: code → emoji, the fixed platform set (D-26), in the order the picker shows them. */
@ConfigurationProperties("app.forum")
public record ForumProperties(Map<String, String> reactions) {

    public boolean isAllowedReaction(String code) {
        return reactions.containsKey(code);
    }
}
