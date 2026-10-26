package uk.gov.pmrv.api.workflow.request.application.requestaction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.netz.api.authorization.core.domain.AppUser;
import uk.gov.netz.api.authorization.rules.domain.ResourceType;
import uk.gov.netz.api.authorization.rules.services.AuthorizationRulesQueryService;
import uk.gov.netz.api.common.constants.RoleTypeConstants;
import uk.gov.netz.api.common.exception.BusinessException;
import uk.gov.netz.api.common.exception.ErrorCode;
import uk.gov.pmrv.api.workflow.request.core.domain.RequestAction;
import uk.gov.pmrv.api.workflow.request.core.domain.dto.RequestActionDTO;
import uk.gov.pmrv.api.workflow.request.core.domain.dto.RequestActionInfoDTO;
import uk.gov.pmrv.api.workflow.request.core.domain.enumeration.RequestActionType;
import uk.gov.pmrv.api.workflow.request.core.repository.RequestActionRepository;
import uk.gov.pmrv.api.workflow.request.core.transform.RequestActionCustomMapperHandler;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestActionQueryServiceTest {

    @InjectMocks
    private RequestActionQueryService service;

    @Mock
    private RequestActionRepository requestActionRepository;
    
    @Mock
    private AuthorizationRulesQueryService authorizationRulesQueryService;
    
    @Mock
    private RequestActionCustomMapperHandler customMapperHandler;

    @Test
    void getRequestActionById() {
        AppUser user = AppUser.builder().userId("user").build();
        Long requestActionId = 1L; 
        String submitterId = "submitterId";
        RequestAction requestAction = RequestAction.builder().id(requestActionId).submitterId(submitterId).submitter("fn ln").build();
        
        when(requestActionRepository.findById(requestActionId)).thenReturn(Optional.of(requestAction));
        when(customMapperHandler.getMapper(any(), any())).thenReturn(Optional.empty());
        
        //invoke
        RequestActionDTO result = service.getRequestActionById(requestActionId, user);
        assertThat(result.getId()).isEqualTo(requestActionId);
        assertThat(result.getSubmitter()).isEqualTo("fn ln");
        
        verify(requestActionRepository, times(1)).findById(requestActionId);
    }
    
    @Test
    void getRequestActionById_not_found() {
        AppUser user = AppUser.builder().userId("user").build();
        Long requestActionId = 1L; 
        
        when(requestActionRepository.findById(requestActionId)).thenReturn(Optional.empty());
        
        //invoke
        BusinessException be = assertThrows(BusinessException.class, () -> service.getRequestActionById(requestActionId, user));
        assertThat(be.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        
        verify(requestActionRepository, times(1)).findById(requestActionId);
    }

    @Test
    void getRequestActionsByRequestId() {
        final String requestId = "1";
        AppUser authUser = AppUser.builder().userId("user").roleType(RoleTypeConstants.OPERATOR).build();
        
        RequestAction requestAction1 = RequestAction.builder()
                .id(1L)
                .type(RequestActionType.INSTALLATION_ACCOUNT_OPENING_APPLICATION_SUBMITTED)
                .submitterId("user1")
                .submitter("fn ln 1")
                .creationDate(LocalDateTime.of(2022, 2, 3, 10, 36, 15))
                .build();
        
        RequestAction requestAction2 = RequestAction.builder()
                .id(2L)
                .type(RequestActionType.INSTALLATION_ACCOUNT_OPENING_ACCOUNT_APPROVED)
                .submitterId("user2")
                .submitter("fn ln 2")
                .creationDate(LocalDateTime.of(2022, 2, 3, 10, 40, 0))
                .build();
        
        List<RequestAction> requestActions = List.of(requestAction1, requestAction2);
        
        when(requestActionRepository.findAllByRequestId(requestId))
            .thenReturn(requestActions);
            
        Set<String> userAllowedRequestActionTypes = Set.of(RequestActionType.INSTALLATION_ACCOUNT_OPENING_APPLICATION_SUBMITTED.name());
        when(authorizationRulesQueryService.findResourceSubTypesByResourceTypeAndRoleType(ResourceType.REQUEST_ACTION, RoleTypeConstants.OPERATOR))
            .thenReturn(userAllowedRequestActionTypes);
        
        List<RequestActionInfoDTO> result = service.getRequestActionsByRequestId(requestId, authUser);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isEqualTo(RequestActionInfoDTO.builder()
                .id(1L)
                .type(RequestActionType.INSTALLATION_ACCOUNT_OPENING_APPLICATION_SUBMITTED)
                .submitter("fn ln 1")
                .creationDate(LocalDateTime.of(2022, 2, 3, 10, 36, 15))
                .build());
        verify(requestActionRepository, times(1)).findAllByRequestId(requestId);
        verify(authorizationRulesQueryService, times(1)).findResourceSubTypesByResourceTypeAndRoleType(ResourceType.REQUEST_ACTION, RoleTypeConstants.OPERATOR);
        verifyNoMoreInteractions(requestActionRepository, authorizationRulesQueryService);
    }
    
    @Test
    void getRequestActionsByRequestId_ordered_most_recent_first() {
        List<RequestActionInfoDTO> result = getRequestActions(
                requestAction(1L, RequestActionType.AER_APPLICATION_SUBMITTED, LocalDateTime.of(2022, 2, 2, 9, 15, 0)),
                requestAction(2L, RequestActionType.AER_APPLICATION_COMPLETED, LocalDateTime.of(2022, 2, 4, 14, 0, 0)),
                requestAction(3L, RequestActionType.AER_APPLICATION_SENT_TO_VERIFIER, LocalDateTime.of(2022, 2, 3, 11, 30, 0)));

        assertThat(result).extracting(RequestActionInfoDTO::getId).containsExactly(2L, 3L, 1L);
    }

    @Test
    void getRequestActionsByRequestId_emissions_sent_to_registry_leads_the_actions_of_its_window() {
        // The three actions were created within a minute of each other, so the registry action leads them
        // even though it was not the last one created.
        List<RequestActionInfoDTO> result = getRequestActions(
                requestAction(1L, RequestActionType.INSTALLATION_REPORTABLE_EMISSIONS_SENT_TO_REGISTRY, LocalDateTime.of(2022, 2, 3, 10, 36, 20)),
                requestAction(2L, RequestActionType.AER_APPLICATION_COMPLETED, LocalDateTime.of(2022, 2, 3, 10, 36, 55)),
                requestAction(3L, RequestActionType.AER_APPLICATION_SUBMITTED, LocalDateTime.of(2022, 2, 3, 10, 36, 5)));

        assertThat(result).extracting(RequestActionInfoDTO::getId).containsExactly(1L, 2L, 3L);
    }

    @Test
    void getRequestActionsByRequestId_emissions_sent_to_registry_leads_a_window_crossing_a_minute() {
        // The window is measured from the registry action, so the minute the actions happen to fall in is irrelevant.
        List<RequestActionInfoDTO> result = getRequestActions(
                requestAction(1L, RequestActionType.INSTALLATION_REPORTABLE_EMISSIONS_SENT_TO_REGISTRY, LocalDateTime.of(2022, 2, 3, 10, 36, 59)),
                requestAction(2L, RequestActionType.AER_APPLICATION_COMPLETED, LocalDateTime.of(2022, 2, 3, 10, 37, 30)),
                requestAction(3L, RequestActionType.AER_APPLICATION_SUBMITTED, LocalDateTime.of(2022, 2, 3, 10, 36, 10)));

        assertThat(result).extracting(RequestActionInfoDTO::getId).containsExactly(1L, 2L, 3L);
    }

    @Test
    void getRequestActionsByRequestId_aviation_emissions_sent_to_registry_leads_the_actions_of_its_window() {
        List<RequestActionInfoDTO> result = getRequestActions(
                requestAction(1L, RequestActionType.AVIATION_REPORTABLE_EMISSIONS_SENT_TO_REGISTRY, LocalDateTime.of(2022, 2, 3, 10, 36, 20)),
                requestAction(2L, RequestActionType.AER_APPLICATION_COMPLETED, LocalDateTime.of(2022, 2, 3, 10, 36, 55)),
                requestAction(3L, RequestActionType.AER_APPLICATION_SUBMITTED, LocalDateTime.of(2022, 2, 3, 10, 36, 5)));

        assertThat(result).extracting(RequestActionInfoDTO::getId).containsExactly(1L, 2L, 3L);
    }

    @Test
    void getRequestActionsByRequestId_actions_created_after_the_window_are_not_overtaken() {
        // Action 2 was created more than a minute after the registry action, so it keeps its place above it.
        List<RequestActionInfoDTO> result = getRequestActions(
                requestAction(1L, RequestActionType.INSTALLATION_REPORTABLE_EMISSIONS_SENT_TO_REGISTRY, LocalDateTime.of(2022, 2, 3, 10, 36, 0)),
                requestAction(2L, RequestActionType.AER_APPLICATION_COMPLETED, LocalDateTime.of(2022, 2, 3, 10, 37, 30)),
                requestAction(3L, RequestActionType.AER_APPLICATION_SUBMITTED, LocalDateTime.of(2022, 2, 3, 10, 35, 50)));

        assertThat(result).extracting(RequestActionInfoDTO::getId).containsExactly(2L, 1L, 3L);
    }

    @Test
    void getRequestActionsByRequestId_emissions_sent_to_registry_outside_the_window_stays_in_chronological_order() {
        // The timeline order only applies within the window, so an older registry action is not lifted above newer actions.
        List<RequestActionInfoDTO> result = getRequestActions(
                requestAction(1L, RequestActionType.INSTALLATION_REPORTABLE_EMISSIONS_SENT_TO_REGISTRY, LocalDateTime.of(2022, 2, 1, 8, 0, 0)),
                requestAction(2L, RequestActionType.AER_APPLICATION_COMPLETED, LocalDateTime.of(2022, 2, 4, 14, 0, 0)),
                requestAction(3L, RequestActionType.AER_APPLICATION_SUBMITTED, LocalDateTime.of(2022, 2, 2, 9, 15, 0)));

        assertThat(result).extracting(RequestActionInfoDTO::getId).containsExactly(2L, 3L, 1L);
    }

    @Test
    void getRequestActionsByRequestId_multiple_emissions_sent_to_registry_in_one_window_ordered_most_recent_first() {
        List<RequestActionInfoDTO> result = getRequestActions(
                requestAction(1L, RequestActionType.INSTALLATION_REPORTABLE_EMISSIONS_SENT_TO_REGISTRY, LocalDateTime.of(2022, 2, 3, 9, 0, 10)),
                requestAction(2L, RequestActionType.AER_APPLICATION_COMPLETED, LocalDateTime.of(2022, 2, 3, 9, 0, 50)),
                requestAction(3L, RequestActionType.INSTALLATION_REPORTABLE_EMISSIONS_SENT_TO_REGISTRY, LocalDateTime.of(2022, 2, 3, 9, 0, 30)));

        assertThat(result).extracting(RequestActionInfoDTO::getId).containsExactly(3L, 1L, 2L);
    }

    @Test
    void getRequestActionsByRequestId_identical_creation_dates_ordered_by_id() {
        LocalDateTime creationDate = LocalDateTime.of(2022, 2, 3, 10, 36, 15);

        List<RequestActionInfoDTO> result = getRequestActions(
                requestAction(1L, RequestActionType.AER_APPLICATION_SUBMITTED, creationDate),
                requestAction(2L, RequestActionType.AER_APPLICATION_SENT_TO_VERIFIER, creationDate));

        assertThat(result).extracting(RequestActionInfoDTO::getId).containsExactly(2L, 1L);
    }

    private List<RequestActionInfoDTO> getRequestActions(RequestAction... requestActions) {
        final String requestId = "1";
        AppUser authUser = AppUser.builder().userId("user").roleType(RoleTypeConstants.REGULATOR).build();

        when(requestActionRepository.findAllByRequestId(requestId)).thenReturn(List.of(requestActions));
        when(authorizationRulesQueryService.findResourceSubTypesByResourceTypeAndRoleType(ResourceType.REQUEST_ACTION,
                RoleTypeConstants.REGULATOR))
            .thenReturn(Arrays.stream(requestActions).map(ra -> ra.getType().name()).collect(Collectors.toSet()));

        return service.getRequestActionsByRequestId(requestId, authUser);
    }

    private RequestAction requestAction(Long id, RequestActionType type, LocalDateTime creationDate) {
        return RequestAction.builder()
                .id(id)
                .type(type)
                .submitterId("user" + id)
                .submitter("fn ln " + id)
                .creationDate(creationDate)
                .build();
    }

}
