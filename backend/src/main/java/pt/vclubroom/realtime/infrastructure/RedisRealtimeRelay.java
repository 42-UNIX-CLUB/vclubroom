package pt.vclubroom.realtime.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Runs on every instance: Redis → local STOMP broker. A user destination reaches only the instance(s) holding that
 * user's sessions, because the user registry is local; the others do nothing.
 */
@Component
class RedisRealtimeRelay implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisRealtimeRelay.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    RedisRealtimeRelay(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            JsonNode wire = objectMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            String destination = wire.get("destination").asText();
            JsonNode envelope = wire.get("envelope");
            if ("USER".equals(wire.get("kind").asText())) {
                messagingTemplate.convertAndSendToUser(wire.get("userId").asText(), destination, envelope);
            } else {
                messagingTemplate.convertAndSend(destination, envelope);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Dropping malformed realtime message", e);                // best effort by design
        }
    }
}
