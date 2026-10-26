package uk.gov.pmrv.api.web.config;

import com.github.benmanes.caffeine.jcache.configuration.CaffeineConfiguration;
import org.springframework.boot.autoconfigure.cache.JCacheManagerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.OptionalLong;

@Configuration
public class RateLimitCacheConfig {

    /** The cache-name of the bucket4j filters that rate limit per client IP. */
    public static final String CLIENT_BUCKETS_CACHE = "rate-limit-client-buckets";

    static final long CLIENT_BUCKETS_MAX_SIZE = 10_000;
    static final Duration CLIENT_BUCKETS_EXPIRE_AFTER_ACCESS = Duration.ofDays(1);

    /**
     * Buckets keyed per client IP can be created by anyone, so unlike the caches created from spring.cache.cache-names
     * this one is bounded, and buckets left idle for longer than any refill period expire.
     */
    @Bean
    public JCacheManagerCustomizer rateLimitClientBucketsCacheCustomizer() {
        CaffeineConfiguration<String, byte[]> configuration = new CaffeineConfiguration<String, byte[]>()
                .setTypes(String.class, byte[].class)
                .setStoreByValue(false)
                .setMaximumSize(OptionalLong.of(CLIENT_BUCKETS_MAX_SIZE))
                .setExpireAfterAccess(OptionalLong.of(CLIENT_BUCKETS_EXPIRE_AFTER_ACCESS.toNanos()));
        return cacheManager -> cacheManager.createCache(CLIENT_BUCKETS_CACHE, configuration);
    }
}
