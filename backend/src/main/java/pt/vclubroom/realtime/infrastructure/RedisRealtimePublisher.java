package pt.vclubroom.realtime.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pt.vclubroom.realtime.api.RealtimePublisher;

/**
 * Publishes to Redis, never to the local broker: every instance, this one included, relays from Redis
 * (RedisRealtimeRelay). One code path, no duplicates, no sticky sessions.
 */
@Component
class RedisRealtimePublisher implements RealtimePublisher {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RealtimeProperties properties;
    private final Clock clock;

    RedisRealtimePublisher(StringRedisTemplate redis, ObjectMapper objectMapper, RealtimeProperties properties,
                           Clock clock) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void toTopic(String destination, String type, Object payload) {
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("kind", "TOPIC");
        wire.put("destination", destination);
        wire.put("envelope", new RealtimeEnvelope(type, payload, clock.instant()));
        publishAfterCommit(wire);
    }

    @Override
    public void toUser(UUID userId, String destination, String type, Object payload) {
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("kind", "USER");
        wire.put("userId", userId.toString());
        wire.put("destination", destination);
        wire.put("envelope", new RealtimeEnvelope(type, payload, clock.instant()));
        publishAfterCommit(wire);
    }

    /** A push about a change that rolls back must never leave: wait for the commit when there is a transaction. */
    private void publishAfterCommit(Map<String, Object> wire) {
        String json = serialize(wire);                 // now: entities may be detached after the commit
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    redis.convertAndSend(properties.redisChannel(), json);
                }
            });
        } else {
            redis.convertAndSend(properties.redisChannel(), json);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Realtime payload is not serializable", e);
        }
    }
}
