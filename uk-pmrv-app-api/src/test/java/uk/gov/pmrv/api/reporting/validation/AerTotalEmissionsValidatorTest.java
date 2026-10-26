package uk.gov.pmrv.api.reporting.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.netz.api.common.exception.BusinessException;
import uk.gov.pmrv.api.common.exception.MetsErrorCode;
import uk.gov.pmrv.api.reporting.domain.Aer;
import uk.gov.pmrv.api.reporting.domain.AerContainer;
import uk.gov.pmrv.api.reporting.domain.AerValidationResult;
import uk.gov.pmrv.api.reporting.domain.AerViolation;
import uk.gov.pmrv.api.reporting.domain.monitoringapproachesemissions.MonitoringApproachEmissions;
import uk.gov.pmrv.api.reporting.domain.verification.AerVerificationData;
import uk.gov.pmrv.api.reporting.domain.verification.AerVerificationReport;
import uk.gov.pmrv.api.reporting.domain.verification.MonitoringApproachTypeEmissions;
import uk.gov.pmrv.api.reporting.domain.verification.OpinionStatement;
import uk.gov.pmrv.api.reporting.service.ReportableEmissionsCalculationService;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AerTotalEmissionsValidatorTest {

    @InjectMocks
    private AerTotalEmissionsValidator validator;

    @Mock
    private ReportableEmissionsCalculationService reportableEmissionsCalculationService;

    @Test
    void validate_negative_total() {
        AerContainer container = containerWithAer();
        when(reportableEmissionsCalculationService.calculateYearEmissions(container))
                .thenReturn(new BigDecimal("-0.00001"));

        AerValidationResult result = validator.validate(container);

        assertFalse(result.isValid());
        assertEquals(1, result.getAerViolations().size());
        assertEquals(AerViolation.AerViolationMessage.TOTAL_EMISSIONS_NEGATIVE.getMessage(),
                result.getAerViolations().get(0).getMessage());
    }

    @Test
    void validate_zero_total() {
        AerContainer container = containerWithAer();
        when(reportableEmissionsCalculationService.calculateYearEmissions(container)).thenReturn(BigDecimal.ZERO);

        assertTrue(validator.validate(container).isValid());
    }

    @Test
    void validate_positive_total() {
        AerContainer container = containerWithAer();
        when(reportableEmissionsCalculationService.calculateYearEmissions(container)).thenReturn(BigDecimal.TEN);

        assertTrue(validator.validate(container).isValid());
    }

    @Test
    void validate_operator_emissions_acceptable_negative_aer_total() {
        AerContainer container = AerContainer.builder()
                .aer(Aer.builder().monitoringApproachEmissions(new MonitoringApproachEmissions()).build())
                .verificationReport(reportWithOpinion(
                        OpinionStatement.builder().operatorEmissionsAcceptable(true).build()))
                .build();
        when(reportableEmissionsCalculationService.calculateYearEmissions(container))
                .thenReturn(new BigDecimal("-1"));

        assertFalse(validator.validate(container).isValid());
    }

    @Test
    void validate_operator_emissions_acceptable_without_aer_skips_calculation() {
        AerContainer container = AerContainer.builder()
                .verificationReport(reportWithOpinion(
                        OpinionStatement.builder().operatorEmissionsAcceptable(true).build()))
                .build();

        assertTrue(validator.validate(container).isValid());
        verifyNoInteractions(reportableEmissionsCalculationService);
    }

    @Test
    void validate_aer_without_monitoring_approach_emissions_skips_calculation() {
        AerContainer container = AerContainer.builder().aer(Aer.builder().build()).build();

        assertTrue(validator.validate(container).isValid());
        verifyNoInteractions(reportableEmissionsCalculationService);
    }

    @Test
    void validate_operator_emissions_not_acceptable_non_negative_verifier_total() {
        AerContainer container = AerContainer.builder()
                .verificationReport(reportWithOpinion(OpinionStatement.builder()
                        .operatorEmissionsAcceptable(false)
                        .monitoringApproachTypeEmissions(Mockito.mock(MonitoringApproachTypeEmissions.class))
                        .build()))
                .build();
        when(reportableEmissionsCalculationService.calculateYearEmissions(container)).thenReturn(BigDecimal.TEN);

        assertTrue(validator.validate(container).isValid());
    }

    @Test
    void validate_verification_report_without_verification_data_skips_calculation() {
        AerContainer container = AerContainer.builder()
                .aer(Aer.builder().monitoringApproachEmissions(new MonitoringApproachEmissions()).build())
                .verificationReport(AerVerificationReport.builder().build())
                .build();

        assertTrue(validator.validate(container).isValid());
        verifyNoInteractions(reportableEmissionsCalculationService);
    }

    @Test
    void validate_operator_emissions_acceptable_not_set_skips_calculation() {
        AerContainer container = AerContainer.builder()
                .aer(Aer.builder().monitoringApproachEmissions(new MonitoringApproachEmissions()).build())
                .verificationReport(reportWithOpinion(OpinionStatement.builder().build()))
                .build();

        assertTrue(validator.validate(container).isValid());
        verifyNoInteractions(reportableEmissionsCalculationService);
    }

    @Test
    void validate_operator_emissions_not_acceptable_without_verifier_emissions_skips_calculation() {
        AerContainer container = AerContainer.builder()
                .verificationReport(reportWithOpinion(
                        OpinionStatement.builder().operatorEmissionsAcceptable(false).build()))
                .build();

        assertTrue(validator.validate(container).isValid());
        verifyNoInteractions(reportableEmissionsCalculationService);
    }

    @Test
    void validate_operator_emissions_not_acceptable_negative_verifier_total() {
        AerContainer container = AerContainer.builder()
                .verificationReport(reportWithOpinion(OpinionStatement.builder()
                        .operatorEmissionsAcceptable(false)
                        .monitoringApproachTypeEmissions(Mockito.mock(MonitoringApproachTypeEmissions.class))
                        .build()))
                .build();
        when(reportableEmissionsCalculationService.calculateYearEmissions(container))
                .thenReturn(new BigDecimal("-5"));

        AerValidationResult result = validator.validate(container);

        assertFalse(result.isValid());
        assertEquals(AerViolation.AerViolationMessage.TOTAL_EMISSIONS_NEGATIVE.getMessage(),
                result.getAerViolations().get(0).getMessage());
    }

    @Test
    void validate_null_aer_without_verification_report_does_not_throw() {
        AerContainer container = AerContainer.builder().build();

        AerValidationResult result = assertDoesNotThrow(() -> validator.validate(container));

        assertTrue(result.isValid());
        verifyNoInteractions(reportableEmissionsCalculationService);
    }

    private AerContainer containerWithAer() {
        return AerContainer.builder()
                .aer(Aer.builder().monitoringApproachEmissions(new MonitoringApproachEmissions()).build())
                .build();
    }

    private AerVerificationReport reportWithOpinion(OpinionStatement opinion) {
        return AerVerificationReport.builder()
                .verificationData(AerVerificationData.builder().opinionStatement(opinion).build())
                .build();
    }

    @Test
    void validate_calculation_fails_with_business_exception_is_not_reported_as_negative() {
        AerContainer container = containerWithAer();
        when(reportableEmissionsCalculationService.calculateYearEmissions(container))
                .thenThrow(new BusinessException(MetsErrorCode.AER_EMISSIONS_CALCULATION_PARAMETER_VALUE_MISSING));

        AerValidationResult result = assertDoesNotThrow(() -> validator.validate(container));

        assertTrue(result.isValid());
        assertTrue(result.getAerViolations().isEmpty());
    }

    @Test
    void validate_unexpected_exception_is_propagated() {
        AerContainer container = containerWithAer();
        when(reportableEmissionsCalculationService.calculateYearEmissions(container))
                .thenThrow(new IllegalStateException("boom"));

        assertThrows(IllegalStateException.class, () -> validator.validate(container));
    }
}
