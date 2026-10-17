package pt.vclubroom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// Login is 42 OAuth2 only, so Boot's generated in-memory user (and its logged password) is switched off.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class ClubroomApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClubroomApplication.class, args);
    }
}
