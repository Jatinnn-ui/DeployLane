package com.deployforge.security;

import com.deployforge.common.error.Exceptions.RateLimitedException;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Fixed window rate limiter backed by Redis counters.
 *
 * <p>Protects the expensive and abusable paths: deployment creation, AI analysis, AI chat and
 * webhook ingest. A fixed window is intentionally simple - it is enough to stop accidental deploy
 * spam and runaway retry loops, and it costs one INCR per request.
 *
 * <p><b>Fail open:</b> if Redis is unreachable the platform keeps serving requests. Losing rate
 * limiting is much less harmful than taking the whole API down, and Redis health is reported
 * separately through Actuator.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private static final String KEY_PREFIX = "df:rl:";

    private final StringRedisTemplate redis;

    public RateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * @param bucket logical limiter name, e.g. {@code deployment}
     * @param subject who is being limited, e.g. a user id or repository full name
     * @param limit maximum number of permits in the window
     * @throws RateLimitedException when the caller exceeds the limit
     */
    public void checkPerMinute(String bucket, String subject, int limit) {
        check(bucket, subject, limit, Duration.ofMinutes(1));
    }

    public void check(String bucket, String subject, int limit, Duration window) {
        long windowIndex = Instant.now().getEpochSecond() / Math.max(1, window.toSeconds());
        String key = KEY_PREFIX + bucket + ":" + subject + ":" + windowIndex;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, window.plusSeconds(5));
            }
            if (count != null && count > limit) {
                throw new RateLimitedException(
                        "Rate limit exceeded for "
                                + bucket
                                + ": at most "
                                + limit
                                + " per "
                                + window.toSeconds()
                                + "s. Try again shortly.");
            }
        } catch (DataAccessException e) {
            log.warn("rate_limit_unavailable bucket={} reason={}", bucket, e.getMessage());
        }
    }
}
