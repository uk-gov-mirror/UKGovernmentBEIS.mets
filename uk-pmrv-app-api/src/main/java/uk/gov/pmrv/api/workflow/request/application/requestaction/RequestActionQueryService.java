package uk.gov.pmrv.api.workflow.request.application.requestaction;

import lombok.RequiredArgsConstructor;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.netz.api.authorization.core.domain.AppUser;
import uk.gov.netz.api.authorization.rules.domain.ResourceType;
import uk.gov.netz.api.authorization.rules.services.AuthorizationRulesQueryService;
import uk.gov.netz.api.common.exception.BusinessException;
import uk.gov.netz.api.common.exception.ErrorCode;
import uk.gov.pmrv.api.workflow.request.core.domain.RequestAction;
import uk.gov.pmrv.api.workflow.request.core.domain.dto.RequestActionDTO;
import uk.gov.pmrv.api.workflow.request.core.domain.dto.RequestActionInfoDTO;
import uk.gov.pmrv.api.workflow.request.core.domain.enumeration.RequestActionType;
import uk.gov.pmrv.api.workflow.request.core.repository.RequestActionRepository;
import uk.gov.pmrv.api.workflow.request.core.transform.RequestActionCustomMapper;
import uk.gov.pmrv.api.workflow.request.core.transform.RequestActionCustomMapperHandler;
import uk.gov.pmrv.api.workflow.request.core.transform.RequestActionMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RequiredArgsConstructor
@Service
public class RequestActionQueryService {
    
    private final RequestActionRepository requestActionRepository;
    private final AuthorizationRulesQueryService authorizationRulesQueryService;
    private final RequestActionMapper requestActionMapper = Mappers.getMapper(RequestActionMapper.class);
    private final RequestActionCustomMapperHandler customMapperHandler;

    /**
     * How long after an action the actions created are considered simultaneous with it so they are ordered by their
     * {@link RequestActionType#getTimelineOrder() timeline order}.
     */
    private static final Duration SIMULTANEOUS_ACTIONS_WINDOW = Duration.ofMinutes(1);

    /**
     * Orders the timeline events, most recent first.
     */
    private static final Comparator<RequestAction> TIMELINE_COMPARATOR = Comparator
            .comparing(RequestAction::getCreationDate, Comparator.reverseOrder())
            .thenComparing(RequestAction::getId, Comparator.reverseOrder());

    @Transactional(readOnly = true)
    public RequestActionDTO getRequestActionById(Long requestActionId, AppUser appUser) {
        final RequestAction requestAction = requestActionRepository.findById(requestActionId).orElseThrow(() ->
            new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        
        final RequestActionType requestActionType = requestAction.getType();
        final String roleType = appUser.getRoleType();
        final Optional<RequestActionCustomMapper> customMapper = customMapperHandler.getMapper(requestActionType, roleType);
        return customMapper.isPresent() ?
            customMapper.get().toRequestActionDTO(requestAction) :
            requestActionMapper.toRequestActionDTO(requestAction);
    }
    
    @Transactional(readOnly = true)
    public List<RequestActionInfoDTO> getRequestActionsByRequestId(String requestId, AppUser authUser) {
        final List<RequestAction> requestActions = requestActionRepository.findAllByRequestId(requestId);
        final List<RequestAction> userGrantedRequestActions = filterUserGrantedRequestActions(authUser, requestActions);
        return sortTimeline(userGrantedRequestActions).stream()
                .map(requestActionMapper::toRequestActionInfoDTO)
                .toList();
    }

    /**
     * Orders the actions most recent first and then lifts every action above the more recent actions of a lower
     * timeline order created within the following {@link #SIMULTANEOUS_ACTIONS_WINDOW} so that the actions created
     * at virtually the same time are displayed in their logical order.
     */
    private static List<RequestAction> sortTimeline(List<RequestAction> requestActions) {
        final List<RequestAction> timeline = new ArrayList<>(requestActions);
        timeline.sort(TIMELINE_COMPARATOR);

        for (int index = 1; index < timeline.size(); index++) {
            final RequestAction requestAction = timeline.get(index);

            int position = index;
            while (position > 0 && overtakes(requestAction, timeline.get(position - 1))) {
                position--;
            }
            if (position < index) {
                timeline.remove(index);
                timeline.add(position, requestAction);
            }
        }
        return timeline;
    }

    /**
     * Whether the action is displayed above the more recent one i.e. it is of a higher timeline order and the more
     * recent action was created within the {@link #SIMULTANEOUS_ACTIONS_WINDOW} that follows it.
     */
    private static boolean overtakes(RequestAction requestAction, RequestAction moreRecentAction) {
        final Duration elapsed = Duration.between(requestAction.getCreationDate(), moreRecentAction.getCreationDate());
        return requestAction.getType().getTimelineOrder() > moreRecentAction.getType().getTimelineOrder()
                && elapsed.compareTo(SIMULTANEOUS_ACTIONS_WINDOW) <= 0;
    }

    private List<RequestAction> filterUserGrantedRequestActions(AppUser authUser, List<RequestAction> requestActions) {
        Set<String> userAllowedRequestActionTypes = authorizationRulesQueryService
                .findResourceSubTypesByResourceTypeAndRoleType(ResourceType.REQUEST_ACTION, authUser.getRoleType());
        
        return requestActions.stream()
                .filter(requestAction -> userAllowedRequestActionTypes.contains(requestAction.getType().name()))
                .toList();
    }

}
