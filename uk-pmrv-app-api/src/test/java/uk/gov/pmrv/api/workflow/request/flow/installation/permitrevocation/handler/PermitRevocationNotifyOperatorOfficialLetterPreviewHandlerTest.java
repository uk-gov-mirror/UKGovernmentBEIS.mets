package uk.gov.pmrv.api.workflow.request.flow.installation.permitrevocation.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.netz.api.files.common.domain.dto.FileDTO;
import uk.gov.pmrv.api.notification.template.domain.dto.templateparams.TemplateParams;
import uk.gov.pmrv.api.notification.template.domain.enumeration.DocumentTemplateType;
import uk.gov.pmrv.api.notification.template.service.DocumentFileGeneratorService;
import uk.gov.pmrv.api.workflow.request.core.domain.Request;
import uk.gov.pmrv.api.workflow.request.core.domain.RequestTask;
import uk.gov.pmrv.api.workflow.request.core.domain.enumeration.RequestTaskType;
import uk.gov.pmrv.api.workflow.request.core.service.RequestTaskService;
import uk.gov.pmrv.api.workflow.request.flow.common.domain.DecisionNotification;
import uk.gov.pmrv.api.workflow.request.flow.installation.common.service.notification.InstallationPreviewOfficialNoticeService;
import uk.gov.pmrv.api.workflow.request.flow.installation.permitrevocation.domain.PermitRevocation;
import uk.gov.pmrv.api.workflow.request.flow.installation.permitrevocation.domain.PermitRevocationApplicationPeerReviewRequestTaskPayload;
import uk.gov.pmrv.api.workflow.request.flow.installation.permitrevocation.domain.PermitRevocationApplicationSubmitRequestTaskPayload;
import uk.gov.pmrv.api.workflow.request.flow.installation.permitrevocation.domain.PermitRevocationRequestPayload;
import uk.gov.pmrv.api.workflow.request.flow.installation.permitrevocation.service.notification.PermitRevocationDocumentTemplateWorkflowParamsProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermitRevocationNotifyOperatorOfficialLetterPreviewHandlerTest {

    @InjectMocks
    private PermitRevocationNotifyOperatorOfficialLetterPreviewHandler handler;

    @Mock
    private RequestTaskService requestTaskService;

    @Mock
    private InstallationPreviewOfficialNoticeService previewOfficialNoticeService;

    @Mock
    private DocumentFileGeneratorService documentFileGeneratorService;

    @Spy
    private PermitRevocationDocumentTemplateWorkflowParamsProvider workflowParamsProvider;

    @Test
    void generateDocument_submit_task_before_revocation_is_copied_to_request_payload() {
        final Long taskId = 2L;
        final DecisionNotification decisionNotification = DecisionNotification.builder().build();
        final PermitRevocation permitRevocation = buildPermitRevocation();
        // until the operator is notified the request payload holds only the payment amount
        final Request request = Request.builder()
                .payload(PermitRevocationRequestPayload.builder().paymentAmount(BigDecimal.valueOf(123.22)).build())
                .build();
        final RequestTask requestTask = RequestTask.builder()
                .type(RequestTaskType.PERMIT_REVOCATION_APPLICATION_SUBMIT)
                .payload(PermitRevocationApplicationSubmitRequestTaskPayload.builder()
                        .permitRevocation(permitRevocation)
                        .feeAmount(BigDecimal.valueOf(123.22))
                        .build())
                .request(request)
                .build();
        final TemplateParams templateParams = TemplateParams.builder().build();
        final FileDTO fileDTO = FileDTO.builder().fileName("filename").build();

        when(requestTaskService.findTaskById(taskId)).thenReturn(requestTask);
        when(previewOfficialNoticeService.generateCommonParamsWithExtraAccountDetails(request, decisionNotification)).thenReturn(templateParams);
        when(documentFileGeneratorService.generateFileDocument(
                DocumentTemplateType.PERMIT_REVOCATION,
                templateParams,
                "permit_revocation_notice.pdf")).thenReturn(fileDTO);

        final FileDTO result = handler.generateDocument(taskId, decisionNotification);

        assertThat(result).isEqualTo(fileDTO);
        assertThat(templateParams.getParams()).containsAllEntriesOf(expectedRevocationParams(permitRevocation));
        verify(previewOfficialNoticeService, times(1)).generateCommonParamsWithExtraAccountDetails(request, decisionNotification);
        verify(documentFileGeneratorService, times(1)).generateFileDocument(
                DocumentTemplateType.PERMIT_REVOCATION,
                templateParams,
                "permit_revocation_notice.pdf");
    }

    @Test
    void generateDocument_peer_review_task() {
        final Long taskId = 2L;
        final DecisionNotification decisionNotification = DecisionNotification.builder().build();
        final PermitRevocation permitRevocation = buildPermitRevocation();
        final PermitRevocation stalePermitRevocation = buildPermitRevocation();
        stalePermitRevocation.setReason("stale reason");
        final Request request = Request.builder()
                .payload(PermitRevocationRequestPayload.builder()
                        .permitRevocation(stalePermitRevocation)
                        .paymentAmount(BigDecimal.valueOf(123.22))
                        .build())
                .build();
        final RequestTask requestTask = RequestTask.builder()
                .type(RequestTaskType.PERMIT_REVOCATION_APPLICATION_PEER_REVIEW)
                .payload(PermitRevocationApplicationPeerReviewRequestTaskPayload.builder()
                        .permitRevocation(permitRevocation)
                        .build())
                .request(request)
                .build();
        final TemplateParams templateParams = TemplateParams.builder().build();
        final FileDTO fileDTO = FileDTO.builder().fileName("filename").build();

        when(requestTaskService.findTaskById(taskId)).thenReturn(requestTask);
        when(previewOfficialNoticeService.generateCommonParamsWithExtraAccountDetails(request, decisionNotification)).thenReturn(templateParams);
        when(documentFileGeneratorService.generateFileDocument(
                DocumentTemplateType.PERMIT_REVOCATION,
                templateParams,
                "permit_revocation_notice.pdf")).thenReturn(fileDTO);

        final FileDTO result = handler.generateDocument(taskId, decisionNotification);

        assertThat(result).isEqualTo(fileDTO);
        assertThat(templateParams.getParams()).containsAllEntriesOf(expectedRevocationParams(permitRevocation));
    }

    private PermitRevocation buildPermitRevocation() {
        return PermitRevocation.builder()
                .reason("reason")
                .effectiveDate(LocalDate.now().plusDays(30))
                .feeCharged(Boolean.TRUE)
                .feeDate(LocalDate.now().plusDays(40))
                .build();
    }

    private Map<String, Object> expectedRevocationParams(PermitRevocation permitRevocation) {
        return Map.of(
                "reason", "reason",
                "feeCharged", Boolean.TRUE,
                "feeAmount", BigDecimal.valueOf(123.22),
                "effectiveDate", Date.from(permitRevocation.getEffectiveDate().atStartOfDay(ZoneId.systemDefault()).toInstant()),
                "feeDate", Date.from(permitRevocation.getFeeDate().atStartOfDay(ZoneId.systemDefault()).toInstant()));
    }
}
