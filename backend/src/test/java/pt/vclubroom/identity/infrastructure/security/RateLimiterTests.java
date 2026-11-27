package pt.vclubroom.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import pt.vclubroom.identity.infrastructure.security.RateLimiter.Bucket;
import pt.vclubroom.shared.security.RateLimitProperties.Limit;

class RateLimiterTests {

    @Test
    @SuppressWarnings("unchecked")
    void aRefusalStaysARefusalWhenCountingItFails() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        doReturn(List.of(0L, 60L, 0L, 4L, 4L)).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenThrow(new RedisConnectionFailureException("Redis went away"));

        RateLimiter.Decision decision = new RateLimiter(redis, Clock.systemUTC())
            .take(List.of(new Bucket("token:t", new Limit(60, 1000))));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(4);
    }
}
