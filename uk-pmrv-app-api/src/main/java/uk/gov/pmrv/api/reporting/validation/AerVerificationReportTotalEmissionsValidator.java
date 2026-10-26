package uk.gov.pmrv.api.reporting.validation;

import org.springframework.stereotype.Service;
import uk.gov.pmrv.api.reporting.domain.AerValidationResult;
import uk.gov.pmrv.api.reporting.domain.AerViolation;
import uk.gov.pmrv.api.reporting.domain.monitoringapproachesemissions.PermitOriginatedData;
import uk.gov.pmrv.api.reporting.domain.verification.AerVerificationData;
import uk.gov.pmrv.api.reporting.domain.verification.AerVerificationReport;
import uk.gov.pmrv.api.reporting.domain.verification.MonitoringApproachTypeEmissions;
import uk.gov.pmrv.api.reporting.domain.verification.OpinionStatement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AerVerificationReportTotalEmissionsValidator implements AerVerificationReportContextValidator {

    @Override
    public AerValidationResult validate(AerVerificationReport verificationReport, PermitOriginatedData permitOriginatedData) {
        List<AerViolation> violations = new ArrayList<>();

        Optional.ofNullable(verificationReport.getVerificationData())
                .map(AerVerificationData::getOpinionStatement)
                .map(OpinionStatement::getMonitoringApproachTypeEmissions)
                .map(MonitoringApproachTypeEmissions::getReportableEmissions)
                .filter(total -> total.signum() < 0)
                .ifPresent(total -> violations.add(new AerViolation(AerVerificationReport.class.getSimpleName(),
                        AerViolation.AerViolationMessage.VERIFICATION_TOTAL_EMISSIONS_NEGATIVE)));

        return AerValidationResult.builder()
                .valid(violations.isEmpty())
                .aerViolations(violations)
                .build();
    }
}
