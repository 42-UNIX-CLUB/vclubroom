package pt.vclubroom.realtime.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.session.Session;
import org.springframework.session.web.socket.config.annotation.AbstractSessionWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

/**
 * STOMP over /ws (realtime.md §7). Each instance runs Spring's in-memory broker for its own connections; Redis
 * carries every push to every instance. /ws itself requires a logged-in session (SecurityConfig). Spring Session ties
 * each socket to its HTTP session: when a session is deleted (logout, or an admin disabling the account), its sockets
 * are closed, so they stop receiving pushes. STOMP frames also count as session activity, keeping an open tab's
 * session alive.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig extends AbstractSessionWebSocketMessageBrokerConfigurer<Session> {

    private final RealtimeProperties properties;
    private final SubscriptionAuthorizationInterceptor authorizationInterceptor;
    private final LiveAccessInterceptor liveAccessInterceptor;

    WebSocketConfig(RealtimeProperties properties, SubscriptionAuthorizationInterceptor authorizationInterceptor,
                    LiveAccessInterceptor liveAccessInterceptor) {
        this.properties = properties;
        this.authorizationInterceptor = authorizationInterceptor;
        this.liveAccessInterceptor = liveAccessInterceptor;
    }

    @Override
    protected void configureStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(properties.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
            .setHeartbeatValue(new long[] {10_000, 10_000})                 // idle proxies keep the connection
            .setTaskScheduler(heartbeatScheduler());
        registry.setApplicationDestinationPrefixes("/app");                // unused: SEND is rejected (push-only)
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        super.configureClientInboundChannel(registration);                 // Spring Session: frames refresh the session
        registration.interceptors(authorizationInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(liveAccessInterceptor);                // access lost after SUBSCRIBE stops the pushes
    }

    @Bean
    ThreadPoolTaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}
