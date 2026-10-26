package uk.gov.pmrv.api.reporting.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.netz.api.common.exception.BusinessException;
import uk.gov.pmrv.api.reporting.domain.Aer;
import uk.gov.pmrv.api.reporting.domain.AerContainer;
import uk.gov.pmrv.api.reporting.domain.AerValidationResult;
import uk.gov.pmrv.api.reporting.domain.AerViolation;
import uk.gov.pmrv.api.reporting.domain.monitoringapproachesemissions.MonitoringApproachEmissions;
import uk.gov.pmrv.api.reporting.domain.verification.AerVerificationData;
import uk.gov.pmrv.api.reporting.domain.verification.AerVerificationReport;
import uk.gov.pmrv.api.reporting.domain.verification.OpinionStatement;
import uk.gov.pmrv.api.reporting.service.ReportableEmissionsCalculationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AerTotalEmissionsValidator implements AerContextValidator {

    private final ReportableEmissionsCalculationService reportableEmissionsCalculationService;

    @Override
    public AerValidationResult validate(AerContainer aerContainer) {
        List<AerViolation> violations = new ArrayList<>();

        if (isCalculable(aerContainer) && isTotalNegative(aerContainer)) {
            violations.add(new AerViolation(AerContainer.class.getSimpleName(),
                    AerViolation.AerViolationMessage.TOTAL_EMISSIONS_NEGATIVE));
        }

        return AerValidationResult.builder()
                .valid(violations.isEmpty())
                .aerViolations(violations)
                .build();
    }

    // A failed calculation means the data is invalid, which the other AER validators already report
    private boolean isTotalNegative(AerContainer aerContainer) {
        try {
            return reportableEmissionsCalculationService.calculateYearEmissions(aerContainer).signum() < 0;
        } catch (BusinessException e) {
            return false;
        }
    }

    private boolean isCalculable(AerContainer container) {
        AerVerificationReport report = container.getVerificationReport();
        if (report != null) {
            OpinionStatement opinion = Optional.ofNullable(report.getVerificationData())
                    .map(AerVerificationData::getOpinionStatement).orElse(null);
            if (opinion == null || opinion.getOperatorEmissionsAcceptable() == null) {
                return false;
            }
            if (!opinion.getOperatorEmissionsAcceptable()) {
                return opinion.getMonitoringApproachTypeEmissions() != null;
            }
        }
        return Optional.ofNullable(container.getAer())
                .map(Aer::getMonitoringApproachEmissions)
                .map(MonitoringApproachEmissions::getMonitoringApproachEmissions)
                .isPresent();
    }
}
