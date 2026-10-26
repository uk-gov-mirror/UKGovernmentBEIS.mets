package uk.gov.pmrv.api.workflow.request.flow.aviation.empissuance.corsia.review.service;

import lombok.RequiredArgsConstructor;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Service;
import uk.gov.pmrv.api.workflow.request.core.domain.Request;
import uk.gov.pmrv.api.workflow.request.core.domain.enumeration.RequestActionPayloadType;
import uk.gov.pmrv.api.workflow.request.core.domain.enumeration.RequestActionType;
import uk.gov.pmrv.api.workflow.request.core.service.RequestService;
import uk.gov.pmrv.api.workflow.request.flow.aviation.common.domain.RequestAviationAccountInfo;
import uk.gov.pmrv.api.workflow.request.flow.aviation.common.service.RequestAviationAccountQueryService;
import uk.gov.pmrv.api.workflow.request.flow.aviation.empissuance.corsia.review.domain.EmpIssuanceCorsiaApplicationDeemedWithdrawnRequestActionPayload;
import uk.gov.pmrv.api.workflow.request.flow.aviation.empissuance.corsia.review.mapper.EmpCorsiaReviewMapper;
import uk.gov.pmrv.api.workflow.request.flow.aviation.empissuance.corsia.submit.domain.EmpIssuanceCorsiaRequestPayload;
import uk.gov.pmrv.api.workflow.request.flow.common.domain.DecisionNotification;
import uk.gov.pmrv.api.workflow.request.flow.common.domain.dto.RequestActionUserInfo;
import uk.gov.pmrv.api.workflow.request.flow.common.service.RequestActionUserInfoResolver;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmpIssuanceCorsiaDeemedWithdrawnAddRequestActionService {

    private final RequestService requestService;
    private final RequestActionUserInfoResolver requestActionUserInfoResolver;
    private final RequestAviationAccountQueryService requestAviationAccountQueryService;
    private static final EmpCorsiaReviewMapper EMP_CORSIA_REVIEW_MAPPER = Mappers.getMapper(EmpCorsiaReviewMapper.class);

    public void addRequestAction(final String requestId) {
        Request request = requestService.findRequestById(requestId);
        EmpIssuanceCorsiaRequestPayload requestPayload = (EmpIssuanceCorsiaRequestPayload) request.getPayload();

        RequestAviationAccountInfo accountInfo =
                requestAviationAccountQueryService.getAccountInfo(request.getAccountId());

        // get users' information
        DecisionNotification notification = requestPayload.getDecisionNotification();
        Map<String, RequestActionUserInfo> usersInfo =
            requestActionUserInfoResolver.getUsersInfo(notification.getOperators(), notification.getSignatory(), request);

        EmpIssuanceCorsiaApplicationDeemedWithdrawnRequestActionPayload requestActionPayload =
            EMP_CORSIA_REVIEW_MAPPER.toEmpIssuanceCorsiaApplicationDeemedWithdrawnRequestActionPayload(
                requestPayload, accountInfo, usersInfo, RequestActionPayloadType.EMP_ISSUANCE_CORSIA_APPLICATION_DEEMED_WITHDRAWN_PAYLOAD);

        requestService.addActionToRequest(request,
            requestActionPayload,
            RequestActionType.EMP_ISSUANCE_CORSIA_APPLICATION_DEEMED_WITHDRAWN,
            requestPayload.getRegulatorReviewer());
    }
}
