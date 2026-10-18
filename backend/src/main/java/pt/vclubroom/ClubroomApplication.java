package pt.vclubroom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// Boot's generated in-memory user (and its logged password) is switched off: 42 OAuth2 login for now,
// local password accounts come with their own UserDetailsService in E02-S02.
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync        // @ApplicationModuleListener runs asynchronously after commit
@EnableScheduling   // reminders, email outbox, event-publication maintenance jobs
public class ClubroomApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClubroomApplication.class, args);
    }
}
