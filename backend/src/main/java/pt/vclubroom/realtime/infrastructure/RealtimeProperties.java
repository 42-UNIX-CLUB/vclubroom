package pt.vclubroom.realtime.infrastructure;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.realtime: the origins allowed to open /ws (CSWSH protection), the Redis channel of the relay, and how often a
 * subscriber's access is checked again while pushes arrive (LiveAccessInterceptor).
 */
@ConfigurationProperties("app.realtime")
public record RealtimeProperties(List<String> allowedOrigins, String redisChannel, Duration accessRecheckInterval) {
}
