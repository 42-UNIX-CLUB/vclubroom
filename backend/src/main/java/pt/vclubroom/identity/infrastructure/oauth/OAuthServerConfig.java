package pt.vclubroom.identity.infrastructure.oauth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/** Spring Authorization Server's stores (E20-S04, E20-S05), in PostgreSQL so every backend instance shares them. */
@Configuration
class OAuthServerConfig {

    @Bean
    RegisteredClientRepository registeredClients(JdbcTemplate jdbc) {
        return new JdbcRegisteredClientRepository(jdbc);
    }
}
