package uk.gov.pmrv.api.user.core.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * Limits the emails that the unauthenticated user flows (registration, forgot password) send to the same recipient.
 */
@Validated
@ConfigurationProperties(prefix = "user-email-rate-limit")
@Getter
@Setter
public class UserEmailRateLimitProperties {

    private boolean enabled = true;

    /** The maximum number of emails of the same type sent to a recipient within the period. */
    @Positive
    private int capacity = 3;

    @NotNull
    @DurationMin(minutes = 1)
    @DurationUnit(ChronoUnit.MINUTES)
    private Duration period = Duration.ofHours(1);
}
