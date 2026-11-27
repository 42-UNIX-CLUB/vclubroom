package pt.vclubroom.shared.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How fast apps may call the API (E20-S03, R-44): token buckets that hold `burst` requests and refill `perHour` an hour.
 * Browser sessions have no such limit; logging in has nginx's. Both numbers must be positive: a bucket that never
 * refills would make the limiter's script fail, and the limiter then lets everything through.
 */
@Validated
@ConfigurationProperties("app.rate-limits")
public record RateLimitProperties(@Valid Limit token, @Valid Limit tokenWrites, @Valid Limit user, @Valid Limit anonymous) {

    /** The defaults, for a group application.yml leaves out (@DefaultValue can't build a Limit from two strings). */
    public RateLimitProperties {
        token = token != null ? token : new Limit(60, 1000);
        tokenWrites = tokenWrites != null ? tokenWrites : new Limit(10, 60);
        user = user != null ? user : new Limit(120, 2000);
        anonymous = anonymous != null ? anonymous : new Limit(60, 3600);
    }

    public record Limit(@Positive int burst, @Positive int perHour) {

        public double perSecond() {
            return perHour / 3600.0;
        }
    }
}
