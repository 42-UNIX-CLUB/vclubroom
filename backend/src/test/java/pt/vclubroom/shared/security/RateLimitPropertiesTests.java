package pt.vclubroom.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** The limits as Spring Boot binds them: defaults for what application.yml leaves out, and no bucket that never refills. */
class RateLimitPropertiesTests {

    private final ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(Limits.class);

    @Test
    void groupsLeftOutGetTheirDefaults() {
        context.withPropertyValues("app.rate-limits.token.burst=5", "app.rate-limits.token.per-hour=50").run(c -> {
            assertThat(c).hasNotFailed();
            RateLimitProperties limits = c.getBean(RateLimitProperties.class);
            assertThat(limits.token()).isEqualTo(new RateLimitProperties.Limit(5, 50));
            assertThat(limits.tokenWrites()).isEqualTo(new RateLimitProperties.Limit(10, 60));
            assertThat(limits.user()).isEqualTo(new RateLimitProperties.Limit(120, 2000));
            assertThat(limits.anonymous()).isEqualTo(new RateLimitProperties.Limit(60, 3600));
        });
    }

    /** A rate of 0 would make the Lua divide by zero, and the limiter let everything through: refused at start-up. */
    @Test
    void aLimitThatNeverRefillsIsRefused() {
        context.withPropertyValues("app.rate-limits.token.burst=60", "app.rate-limits.token.per-hour=0")
            .run(c -> assertThat(c).hasFailed());
        context.withPropertyValues("app.rate-limits.anonymous.burst=0", "app.rate-limits.anonymous.per-hour=3600")
            .run(c -> assertThat(c).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RateLimitProperties.class)
    static class Limits {
    }
}
