package uk.gov.pmrv.api.user.core.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.netz.api.common.exception.BusinessException;
import uk.gov.pmrv.api.common.exception.MetsErrorCode;
import uk.gov.pmrv.api.notification.template.domain.enumeration.PmrvNotificationTemplateName;
import uk.gov.pmrv.api.user.core.config.UserEmailRateLimitProperties;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limits the emails that unauthenticated requests can trigger to the same recipient, so that the registration and
 * forgot password flows cannot be used to flood an inbox. Requests per client are limited separately by the bucket4j
 * filters. The counts are kept in memory, so the limit applies per application instance.
 */
@Log4j2
@Service
public class UserEmailRateLimiter {

    static final long MAX_TRACKED_RECIPIENTS = 10_000;

    private final UserEmailRateLimitProperties properties;
    private final Cache<String, AtomicInteger> requestsPerRecipient;

    @Autowired
    public UserEmailRateLimiter(UserEmailRateLimitProperties properties) {
        this(properties, Ticker.systemTicker());
    }

    UserEmailRateLimiter(UserEmailRateLimitProperties properties, Ticker ticker) {
        this.properties = properties;
        this.requestsPerRecipient = Caffeine.newBuilder()
                .expireAfterWrite(properties.getPeriod())
                .ticker(ticker)
                .build();
    }

    /**
     * Records a request to send an email of the given template to the recipient.
     *
     * @throws BusinessException {@link MetsErrorCode#TOO_MANY_REQUESTS} if the recipient has already been sent the
     * allowed number of such emails within the period or a new recipient cannot be tracked because the cache is full
     */
    public void consume(PmrvNotificationTemplateName templateName, String email) {
        if (!properties.isEnabled()) {
            return;
        }

        String key = templateName.name() + ":" + normalise(email);
        AtomicInteger counter = requestsPerRecipient.getIfPresent(key);
        if (counter == null) {
            counter = createCounterIfAbsent(key, templateName);
        }
        int requests = counter.incrementAndGet();
        if (requests > properties.getCapacity()) {
            throw new BusinessException(MetsErrorCode.TOO_MANY_REQUESTS);
        }
    }

    /**
     * Serializes admission so concurrent requests cannot exceed the bound or replace each other's counters.
     * Active quotas must expire naturally: size-based eviction would grant an evicted recipient a fresh quota.
     */
    private synchronized AtomicInteger createCounterIfAbsent(String key, PmrvNotificationTemplateName templateName) {
        AtomicInteger counter = requestsPerRecipient.getIfPresent(key);
        if (counter != null) {
            return counter;
        }

        if (requestsPerRecipient.estimatedSize() >= MAX_TRACKED_RECIPIENTS) {
            requestsPerRecipient.cleanUp();
            if (requestsPerRecipient.estimatedSize() >= MAX_TRACKED_RECIPIENTS) {
                log.warn("Rejected {} email request because the maximum of {} tracked recipients has been reached",
                        templateName, MAX_TRACKED_RECIPIENTS);
                throw new BusinessException(MetsErrorCode.TOO_MANY_REQUESTS);
            }
        }

        counter = new AtomicInteger();
        requestsPerRecipient.put(key, counter);
        return counter;
    }

    /**
     * Ignores case and sub-addressing, as e.g. User+1@example.com is delivered to the user@example.com inbox.
     */
    private static String normalise(String email) {
        String normalised = StringUtils.trimToEmpty(email).toLowerCase(Locale.ROOT);
        int at = normalised.lastIndexOf('@');
        int plus = normalised.indexOf('+');
        return plus >= 0 && plus < at
                ? normalised.substring(0, plus) + normalised.substring(at)
                : normalised;
    }
}
