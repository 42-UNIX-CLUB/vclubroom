package pt.vclubroom.identity.infrastructure.security;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import pt.vclubroom.identity.api.ApiThrottling;
import pt.vclubroom.shared.security.RateLimitProperties.Limit;

/**
 * Token buckets in Redis (E20-S03), so every backend instance shares them: each request takes one token from every
 * bucket it falls in, or from none when one of them is empty. Buckets refill continuously, and expire once full.
 * Refusals are counted per minute for the health card.
 */
@Component
class RateLimiter implements ApiThrottling {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    /** KEYS: the buckets. ARGV: burst and per-second refill of each. Returns allowed, limit, remaining, reset, retry. */
    private static final DefaultRedisScript<List> TAKE = new DefaultRedisScript<>("""
        local t = redis.call('TIME')
        local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
        local buckets, allowed = {}, 1
        for i = 1, #KEYS do
          local burst, rate = tonumber(ARGV[2 * i - 1]), tonumber(ARGV[2 * i])
          local saved = redis.call('HMGET', KEYS[i], 'tokens', 'at')
          local tokens, at = tonumber(saved[1]), tonumber(saved[2])
          if tokens == nil then tokens, at = burst, now end
          tokens = math.min(burst, tokens + math.max(0, now - at) / 1000 * rate)
          if tokens < 1 then allowed = 0 end
          buckets[i] = {tokens, burst, rate}
        end
        local tightest, retry = 1, 0
        for i = 1, #KEYS do
          local b = buckets[i]
          if allowed == 1 then b[1] = b[1] - 1 end
          redis.call('HSET', KEYS[i], 'tokens', tostring(b[1]), 'at', tostring(now))
          redis.call('PEXPIRE', KEYS[i], math.ceil(b[2] / b[3] * 1000) + 1000)
          if b[1] < 1 then retry = math.max(retry, math.ceil((1 - b[1]) / b[3])) end
          if b[1] / b[2] < buckets[tightest][1] / buckets[tightest][2] then tightest = i end
        end
        local b = buckets[tightest]
        return {allowed, b[2], math.max(0, math.floor(b[1])), math.ceil((b[2] - b[1]) / b[3]), retry}
        """, List.class);

    private final StringRedisTemplate redis;
    private final Clock clock;

    RateLimiter(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    record Bucket(String key, Limit limit) {
    }

    /** What the RateLimit-* headers say: about the bucket closest to empty. */
    record Decision(boolean allowed, long limit, long remaining, long resetSeconds, long retryAfterSeconds) {
    }

    /** One request against these buckets. Without Redis, everything is let through rather than refused. */
    Decision take(List<Bucket> buckets) {
        List<String> keys = buckets.stream().map(b -> "ratelimit:" + b.key()).toList();
        List<String> args = new ArrayList<>();
        buckets.forEach(b -> {
            args.add(Integer.toString(b.limit().burst()));
            args.add(Double.toString(b.limit().perSecond()));
        });
        try {
            List<?> r = redis.execute(TAKE, keys, args.toArray());
            Decision decision = new Decision(((Number) r.get(0)).longValue() == 1, ((Number) r.get(1)).longValue(),
                ((Number) r.get(2)).longValue(), ((Number) r.get(3)).longValue(), ((Number) r.get(4)).longValue());
            if (!decision.allowed()) {
                String minute = refusedKey(currentMinute());
                redis.opsForValue().increment(minute);
                redis.expire(minute, 61, TimeUnit.MINUTES);
            }
            return decision;
        } catch (DataAccessException e) {
            log.warn("Rate limits not checked, Redis failed: {}", e.getMessage());
            return new Decision(true, 0, 0, 0, 0);
        }
    }

    @Override
    public long refusedLastHour() {
        long now = currentMinute();
        List<String> keys = LongStream.range(0, 60).mapToObj(m -> refusedKey(now - m)).toList();
        try {
            List<String> counts = redis.opsForValue().multiGet(keys);
            return counts == null ? 0 : counts.stream().filter(Objects::nonNull).mapToLong(Long::parseLong).sum();
        } catch (DataAccessException e) {
            return 0;
        }
    }

    private long currentMinute() {
        return clock.millis() / 60_000;
    }

    private static String refusedKey(long minute) {
        return "ratelimit:refused:" + minute;
    }
}
