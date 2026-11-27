package pt.vclubroom.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How fast apps may call the API (E20-S03, R-44): token buckets that hold `burst` requests and refill `perHour` an hour.
 * Browser sessions have no such limit; logging in has nginx's.
 */
@ConfigurationProperties("app.rate-limits")
public record RateLimitProperties(
    @DefaultValue({"60", "1000"}) Limit token,
    @DefaultValue({"10", "60"}) Limit tokenWrites,
    @DefaultValue({"120", "2000"}) Limit user,
    @DefaultValue({"60", "3600"}) Limit anonymous) {

    public record Limit(int burst, int perHour) {

        public double perSecond() {
            return perHour / 3600.0;
        }
    }
}
