package uk.gov.pmrv.api.user.core.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.netz.api.common.exception.BusinessException;
import uk.gov.pmrv.api.common.exception.MetsErrorCode;
import uk.gov.pmrv.api.user.core.config.UserEmailRateLimitProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.pmrv.api.notification.template.domain.enumeration.PmrvNotificationTemplateName.EMAIL_CONFIRMATION;
import static uk.gov.pmrv.api.notification.template.domain.enumeration.PmrvNotificationTemplateName.RESET_PASSWORD_REQUEST;

class UserEmailRateLimiterTest {

    private static final String EMAIL = "user@example.com";

    private final AtomicLong nanos = new AtomicLong();
    private UserEmailRateLimitProperties properties;

    @BeforeEach
    void setUp() {
        properties = new UserEmailRateLimitProperties();
        properties.setCapacity(3);
        properties.setPeriod(Duration.ofHours(1));
    }

    @Test
    void consume_rejects_requests_over_the_capacity() {
        UserEmailRateLimiter limiter = createLimiter();

        consumeTimes(limiter, 3, EMAIL);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> limiter.consume(RESET_PASSWORD_REQUEST, EMAIL));
        assertThat(ex.getErrorCode()).isEqualTo(MetsErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    void consume_allows_requests_again_after_the_period() {
        UserEmailRateLimiter limiter = createLimiter();
        consumeTimes(limiter, 3, EMAIL);

        nanos.addAndGet(Duration.ofMinutes(59).toNanos());
        assertThrows(BusinessException.class, () -> limiter.consume(RESET_PASSWORD_REQUEST, EMAIL));

        nanos.addAndGet(Duration.ofMinutes(1).toNanos());
        consumeTimes(limiter, 3, EMAIL);
    }

    @Test
    void consume_limits_each_recipient_separately() {
        UserEmailRateLimiter limiter = createLimiter();
        consumeTimes(limiter, 3, EMAIL);

        assertThatCode(() -> limiter.consume(RESET_PASSWORD_REQUEST, "other@example.com")).doesNotThrowAnyException();
    }

    @Test
    void consume_limits_each_template_separately() {
        UserEmailRateLimiter limiter = createLimiter();
        consumeTimes(limiter, 3, EMAIL);

        assertThatCode(() -> limiter.consume(EMAIL_CONFIRMATION, EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void consume_ignores_case_whitespace_and_sub_address() {
        UserEmailRateLimiter limiter = createLimiter();

        limiter.consume(RESET_PASSWORD_REQUEST, "User@Example.com");
        limiter.consume(RESET_PASSWORD_REQUEST, " user@example.com ");
        limiter.consume(RESET_PASSWORD_REQUEST, "user+1@example.com");

        assertThrows(BusinessException.class, () -> limiter.consume(RESET_PASSWORD_REQUEST, "USER+2@EXAMPLE.COM"));
    }

    @Test
    void consume_rejects_new_recipients_without_resetting_existing_quotas_when_full() {
        UserEmailRateLimiter limiter = createLimiter();
        consumeTimes(limiter, 3, EMAIL);
        fillOtherRecipients(limiter, UserEmailRateLimiter.MAX_TRACKED_RECIPIENTS - 1);

        for (int i = 0; i < 100; i++) {
            assertRateLimited(limiter, "new" + i + "@example.com");
        }

        assertRateLimited(limiter, EMAIL);
        // A full cache must still allow tracked recipients to use their remaining quota.
        consumeTimes(limiter, 2, "other0@example.com");
        assertRateLimited(limiter, "other0@example.com");
    }

    @Test
    void consume_reuses_expired_slots_without_resetting_unexpired_quotas() {
        UserEmailRateLimiter limiter = createLimiter();
        consumeTimes(limiter, 3, EMAIL);
        nanos.addAndGet(Duration.ofMinutes(30).toNanos());
        fillOtherRecipients(limiter, UserEmailRateLimiter.MAX_TRACKED_RECIPIENTS - 1);
        consumeTimes(limiter, 2, "other0@example.com");

        nanos.addAndGet(Duration.ofMinutes(29).toNanos());
        assertRateLimited(limiter, "new@example.com");
        assertRateLimited(limiter, EMAIL);

        nanos.addAndGet(Duration.ofMinutes(1).toNanos());
        consumeTimes(limiter, 3, EMAIL);
        assertRateLimited(limiter, "other0@example.com");
        assertRateLimited(limiter, "new@example.com");

        nanos.addAndGet(Duration.ofMinutes(30).toNanos());
        consumeTimes(limiter, 3, "new@example.com");
        assertRateLimited(limiter, EMAIL);
    }

    @Test
    void consume_admits_only_one_new_recipient_when_concurrent_requests_compete_for_last_slot() throws Exception {
        UserEmailRateLimiter limiter = createLimiter();
        fillOtherRecipients(limiter, UserEmailRateLimiter.MAX_TRACKED_RECIPIENTS - 1);
        List<String> emails = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            emails.add("new" + i + "@example.com");
        }

        assertThat(consumeConcurrently(limiter, emails)).isEqualTo(1);
        assertRateLimited(limiter, "another@example.com");
    }

    @Test
    void consume_shares_one_counter_for_concurrent_requests_to_a_new_recipient() throws Exception {
        UserEmailRateLimiter limiter = createLimiter();
        List<String> emails = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            emails.add(EMAIL);
        }

        assertThat(consumeConcurrently(limiter, emails)).isEqualTo(properties.getCapacity());
        assertRateLimited(limiter, EMAIL);
    }

    @Test
    void consume_does_not_limit_when_disabled() {
        properties.setEnabled(false);
        UserEmailRateLimiter limiter = createLimiter();

        assertThatCode(() -> consumeTimes(limiter, 10, EMAIL)).doesNotThrowAnyException();
    }

    private UserEmailRateLimiter createLimiter() {
        return new UserEmailRateLimiter(properties, nanos::get);
    }

    private void consumeTimes(UserEmailRateLimiter limiter, int times, String email) {
        for (int i = 0; i < times; i++) {
            limiter.consume(RESET_PASSWORD_REQUEST, email);
        }
    }

    private void fillOtherRecipients(UserEmailRateLimiter limiter, long count) {
        for (int i = 0; i < count; i++) {
            limiter.consume(RESET_PASSWORD_REQUEST, "other" + i + "@example.com");
        }
    }

    private void assertRateLimited(UserEmailRateLimiter limiter, String email) {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> limiter.consume(RESET_PASSWORD_REQUEST, email));
        assertThat(ex.getErrorCode()).isEqualTo(MetsErrorCode.TOO_MANY_REQUESTS);
    }

    private int consumeConcurrently(UserEmailRateLimiter limiter, List<String> emails) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(emails.size())) {
            for (String email : emails) {
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        limiter.consume(RESET_PASSWORD_REQUEST, email);
                        return true;
                    } catch (BusinessException ex) {
                        assertThat(ex.getErrorCode()).isEqualTo(MetsErrorCode.TOO_MANY_REQUESTS);
                        return false;
                    }
                }));
            }
            start.countDown();

            int accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }
            return accepted;
        }
    }
}
