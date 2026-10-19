package pt.vclubroom.realtime.infrastructure;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.realtime: the origins allowed to open /ws (CSWSH protection) and the Redis channel of the relay. */
@ConfigurationProperties("app.realtime")
public record RealtimeProperties(List<String> allowedOrigins, String redisChannel) {
}
