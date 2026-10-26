package uk.gov.pmrv.api.reporting.validation;

import org.junit.jupiter.api.Test;
import uk.gov.pmrv.api.reporting.domain.AerValidationResult;
import uk.gov.pmrv.api.reporting.domain.AerViolation;
import uk.gov.pmrv.api.reporting.domain.verification.AerVerificationData;
import uk.gov.pmrv.api.reporting.domain.verification.AerVerificationReport;
import uk.gov.pmrv.api.reporting.domain.verification.MonitoringApproachTypeEmissions;
import uk.gov.pmrv.api.reporting.domain.verification.OpinionStatement;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AerVerificationReportTotalEmissionsValidatorTest {

    private final AerVerificationReportTotalEmissionsValidator validator =
            new AerVerificationReportTotalEmissionsValidator();

    @Test
    void validate_negative_total() {
        AerValidationResult result = validator.validate(reportWithTotal(new BigDecimal("-1")), null);

        assertFalse(result.isValid());
        assertEquals(1, result.getAerViolations().size());
        assertEquals(AerViolation.AerViolationMessage.VERIFICATION_TOTAL_EMISSIONS_NEGATIVE.getMessage(),
                result.getAerViolations().get(0).getMessage());
    }

    @Test
    void validate_zero_total() {
        assertTrue(validator.validate(reportWithTotal(BigDecimal.ZERO), null).isValid());
    }

    @Test
    void validate_positive_total() {
        assertTrue(validator.validate(reportWithTotal(BigDecimal.TEN), null).isValid());
    }

    @Test
    void validate_no_verification_data() {
        assertTrue(validator.validate(AerVerificationReport.builder().build(), null).isValid());
    }

    private AerVerificationReport reportWithTotal(BigDecimal total) {
        MonitoringApproachTypeEmissions emissions = mock(MonitoringApproachTypeEmissions.class);
        when(emissions.getReportableEmissions()).thenReturn(total);
        return AerVerificationReport.builder()
                .verificationData(AerVerificationData.builder()
                        .opinionStatement(OpinionStatement.builder().monitoringApproachTypeEmissions(emissions).build())
                        .build())
                .build();
    }
}
