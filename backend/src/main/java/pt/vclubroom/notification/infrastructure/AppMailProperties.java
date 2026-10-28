package pt.vclubroom.notification.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** app.mail.*: named AppMailProperties to avoid confusion with Spring Boot's own spring.mail properties. */
@ConfigurationProperties("app.mail")
public record AppMailProperties(@DefaultValue("smtp") String transport, String from, String replyTo) {
}
