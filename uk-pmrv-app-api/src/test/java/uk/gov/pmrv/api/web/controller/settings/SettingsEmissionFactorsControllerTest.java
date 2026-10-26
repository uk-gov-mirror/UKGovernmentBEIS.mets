package uk.gov.pmrv.api.web.controller.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.aop.framework.AopProxy;
import org.springframework.aop.framework.DefaultAopProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.gov.netz.api.authorization.core.domain.AppUser;
import uk.gov.netz.api.authorization.rules.services.AppUserAuthorizationService;
import uk.gov.netz.api.authorization.rules.services.RoleAuthorizationService;
import uk.gov.netz.api.common.constants.RoleTypeConstants;
import uk.gov.netz.api.common.exception.BusinessException;
import uk.gov.netz.api.common.exception.ErrorCode;
import uk.gov.netz.api.security.AppSecurityComponent;
import uk.gov.netz.api.security.AuthorizationAspectUserResolver;
import uk.gov.netz.api.security.AuthorizedAspect;
import uk.gov.netz.api.security.AuthorizedRoleAspect;
import uk.gov.netz.api.token.FileToken;
import uk.gov.pmrv.api.common.exception.MetsErrorCode;
import uk.gov.pmrv.api.reporting.domain.EmissionFactorMeasurementUnit;
import uk.gov.pmrv.api.reporting.domain.NCVMeasurementUnit;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsDetailDTO;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsHistoryEntryDTO;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsHistoryRecordDTO;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsHistoryResponseDTO;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsNationalInventoryEntryDTO;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsRegionalNaturalGasEntryDTO;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsUploadDTO;
import uk.gov.pmrv.api.settings.service.SettingsEmissionFactorsService;
import uk.gov.pmrv.api.web.config.AppUserArgumentResolver;
import uk.gov.pmrv.api.web.controller.exception.ExceptionControllerAdvice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SettingsEmissionFactorsControllerTest {

    private static final String BASE_PATH = "/v1.0/installation/settings/emission-factors";

    @InjectMocks
    private SettingsEmissionFactorsController controller;

    @Mock
    private SettingsEmissionFactorsService settingsEmissionFactorsService;

    @Mock
    private AppSecurityComponent appSecurityComponent;

    @Mock
    private AppUserAuthorizationService appUserAuthorizationService;

    @Mock
    private RoleAuthorizationService roleAuthorizationService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        AuthorizationAspectUserResolver authorizationAspectUserResolver =
                new AuthorizationAspectUserResolver(appSecurityComponent);
        AuthorizedAspect authorizedAspect =
                new AuthorizedAspect(appUserAuthorizationService, authorizationAspectUserResolver);
        AuthorizedRoleAspect authorizedRoleAspect =
                new AuthorizedRoleAspect(roleAuthorizationService, authorizationAspectUserResolver);
        AspectJProxyFactory aspectJProxyFactory = new AspectJProxyFactory(controller);
        aspectJProxyFactory.addAspect(authorizedAspect);
        aspectJProxyFactory.addAspect(authorizedRoleAspect);
        DefaultAopProxyFactory proxyFactory = new DefaultAopProxyFactory();
        AopProxy aopProxy = proxyFactory.createAopProxy(aspectJProxyFactory);
        controller = (SettingsEmissionFactorsController) aopProxy.getProxy();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ExceptionControllerAdvice())
                .setCustomArgumentResolvers(new AppUserArgumentResolver(appSecurityComponent))
                .addFilters(new FilterChainProxy(Collections.emptyList()))
                .build();
    }

    @Test
    void getEmissionFactors_returnsEntries() throws Exception {
        AppUser appUser = AppUser.builder().userId("user1").roleType(RoleTypeConstants.REGULATOR).build();
        when(appSecurityComponent.getAuthenticatedUser()).thenReturn(appUser);

        List<EmissionFactorsHistoryEntryDTO> entries = List.of(
                EmissionFactorsHistoryEntryDTO.builder()
                        .referenceYear(2026)
                        .fileUuid(UUID.randomUUID())
                        .fileName("uk-ggi-carbon-emission-factors-and-calorific-values-2026.xlsx")
                        .uploadedAt(LocalDate.of(2026, 6, 1))
                        .build(),
                EmissionFactorsHistoryEntryDTO.builder()
                        .referenceYear(2025)
                        .fileUuid(UUID.randomUUID())
                        .fileName("uk-ggi-carbon-emission-factors-and-calorific-values-2025.xlsx")
                        .uploadedAt(LocalDate.of(2025, 6, 1))
                        .build()
        );
        when(settingsEmissionFactorsService.getEmissionFactors()).thenReturn(entries);

        mockMvc.perform(MockMvcRequestBuilders.get(BASE_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].referenceYear").value(2026))
                .andExpect(jsonPath("$[0].fileName").value("uk-ggi-carbon-emission-factors-and-calorific-values-2026.xlsx"))
                .andExpect(jsonPath("$[1].referenceYear").value(2025));

        verify(settingsEmissionFactorsService).getEmissionFactors();
    }

    @Test
    void getEmissionFactors_forbidden() throws Exception {
        AppUser appUser = AppUser.builder().userId("user1").roleType(RoleTypeConstants.REGULATOR).build();
        when(appSecurityComponent.getAuthenticatedUser()).thenReturn(appUser);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(appUserAuthorizationService)
                .authorize(appUser, "getEmissionFactors");

        mockMvc.perform(MockMvcRequestBuilders.get(BASE_PATH))
                .andExpect(status().isForbidden());

        verifyNoInteractions(settingsEmissionFactorsService);
    }

    @Test
    void uploadEmissionFactors_returnsOkWithParsedData() throws Exception {
        AppUser appUser = AppUser.builder().userId("user1").roleType(RoleTypeConstants.REGULATOR).build();
        when(appSecurityComponent.getAuthenticatedUser()).thenReturn(appUser);

        EmissionFactorsDetailDTO parsedData = EmissionFactorsDetailDTO.builder()
                .nationalInventory(List.of(EmissionFactorsNationalInventoryEntryDTO.builder()
                        .sectorName("1A1a")
                        .emissionFactor(BigDecimal.valueOf(93.68))
                        .build()))
                .regionalNaturalGas(List.of(EmissionFactorsRegionalNaturalGasEntryDTO.builder()
                        .chargingZoneCode("EA")
                        .chargingZoneName("Eastern")
                        .build()))
                .build();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "uk-ggi-carbon-emission-factors-and-calorific-values-2026.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[10]);

        UUID fileUuid = UUID.randomUUID();
        when(settingsEmissionFactorsService.uploadEmissionFactors(eq(2026), any(), eq(appUser)))
                .thenReturn(EmissionFactorsUploadDTO.builder().fileUuid(fileUuid).data(parsedData).build());

        mockMvc.perform(MockMvcRequestBuilders.multipart(BASE_PATH)
                        .file(file)
                        .param("referenceYear", "2026")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileUuid").value(fileUuid.toString()))
                .andExpect(jsonPath("$.data.nationalInventory[0].sectorName").value("1A1a"))
                .andExpect(jsonPath("$.data.regionalNaturalGas[0].chargingZoneCode").value("EA"));

        verify(settingsEmissionFactorsService).uploadEmissionFactors(eq(2026), any(), eq(appUser));
    }

    @Test
    void uploadEmissionFactors_forbidden() throws Exception {
        AppUser appUser = AppUser.builder().userId("user1").roleType(RoleTypeConstants.REGULATOR).build();
        when(appSecurityComponent.getAuthenticatedUser()).thenReturn(appUser);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(appUserAuthorizationService)
                .authorize(appUser, "uploadEmissionFactors");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "uk-ggi-carbon-emission-factors-and-calorific-values-2026.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[10]);

        mockMvc.perform(MockMvcRequestBuilders.multipart(BASE_PATH)
                        .file(file)
                        .param("referenceYear", "2026")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isForbidden());

        verifyNoInteractions(settingsEmissionFactorsService);
    }

    @Test
    void getEmissionFactorsDetail_returnsDetail() throws Exception {
        AppUser appUser = AppUser.builder().userId("user1").roleType(RoleTypeConstants.REGULATOR).build();
        when(appSecurityComponent.getAuthenticatedUser()).thenReturn(appUser);

        EmissionFactorsDetailDTO detail = EmissionFactorsDetailDTO.builder()
                .nationalInventory(List.of(
                        EmissionFactorsNationalInventoryEntryDTO.builder()
                                .id(1L)
                                .sectorName("1A1a")
                                .fuel("Coal")
                                .emissionFactor(BigDecimal.valueOf(93.68))
                                .netCalorificValue(BigDecimal.valueOf(23.62))
                                .oxidationFactor(BigDecimal.valueOf(0.9827))
                                .ncvMeasurementUnit(NCVMeasurementUnit.GJ_PER_TONNE)
                                .efMeasurementUnit(EmissionFactorMeasurementUnit.TONNES_OF_CO2_PER_TJ)
                                .build()
                ))
                .regionalNaturalGas(List.of(
                        EmissionFactorsRegionalNaturalGasEntryDTO.builder()
                                .chargingZoneCode("EA")
                                .chargingZoneName("Eastern")
                                .emissionFactor(BigDecimal.valueOf(56.39))
                                .netCalorificValue(BigDecimal.valueOf(0.03739))
                                .calculationFactor(new BigDecimal("0.9476"))
                                .build()
                ))
                .build();
        when(settingsEmissionFactorsService.getEmissionFactorsDetail(2026)).thenReturn(detail);

        mockMvc.perform(MockMvcRequestBuilders.get(BASE_PATH + "/2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nationalInventory").isArray())
                .andExpect(jsonPath("$.nationalInventory.length()").value(1))
                .andExpect(jsonPath("$.nationalInventory[0].id").value(1))
                .andExpect(jsonPath("$.nationalInventory[0].sectorName").value("1A1a"))
                .andExpect(jsonPath("$.nationalInventory[0].fuel").value("Coal"))
                .andExpect(jsonPath("$.nationalInventory[0].ncvMeasurementUnit").value("GJ_PER_TONNE"))
                .andExpect(jsonPath("$.regionalNaturalGas").isArray())
                .andExpect(jsonPath("$.regionalNaturalGas.length()").value(1))
                .andExpect(jsonPath("$.regionalNaturalGas[0].chargingZoneCode").value("EA"))
                .andExpect(jsonPath("$.regionalNaturalGas[0].chargingZoneName").value("Eastern"));

        verify(settingsEmissionFactorsService).getEmissionFactorsDetail(2026);
    }

    @Test
    void getEmissionFactorsDetail_notFound() throws Exception {
        AppUser appUser = AppUser.builder().userId("user1").roleType(RoleTypeConstants.REGULATOR).build();
        when(appSecurityComponent.getAuthenticatedUser()).thenReturn(appUser);
        when(settingsEmissionFactorsService.getEmissionFactorsDetail(2026))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(MockMvcRequestBuilders.get(BASE_PATH + "/2026"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getEmissionFactorsDetail_forbidden() throws Exception {
        AppUser appUser = AppUser.builder().userId("user1").roleType(RoleTypeConstants.REGULATOR).build();
        when(appSecurityComponent.getAuthenticatedUser()).thenReturn(appUser);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(appUserAuthorizationService)
                .authorize(appUser, "getEmissionFactorsDetail");

        mockMvc.perform(MockMvcRequestBuilders.get(BASE_PATH + "/2026"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(settingsEmissionFactorsService);
    }

    @Test
    void submitEmissionFactors_returnsNoContent() throws Exception {
        AppUser appUser = AppUser.builder().userId("user1").roleType(RoleTypeConstants.REGULATOR).build();
        when(appSecurityComponent.getAuthenticatedUser()).thenReturn(appUser);
        UUID fileUuid = UUID.randomUUID();

        mockMvc.perform(MockMvcRequestBuilders.post(BASE_PATH + "/uploads/" + fileUuid + "/submit"))
                .andExpect(status().isNoContent());

        verify(settingsEmissionFactorsService).submitEmissionFactors(fileUuid, appUser);
    }

    @Test
    void submitEmissionFactors_uploadNotFound_returnsBadRequestWithFileUuid() throws Exception {
        AppUser appUser = AppUser.builder().userId("user1").roleType(RoleTypeConstants.REGULATOR).build();
        when(appSecurityComponent.getAuthenticatedUser()).thenReturn(appUser);
        UUID fileUuid = UUID.randomUUID();
        doThrow(new BusinessException(MetsErrorCode.EMISSION_FACTORS_UPLOAD_NOT_FOUND, fileUuid))
                .when(settingsEmissionFactorsService)
                .submitEmissionFactors(fileUuid, appUser);

        mockMvc.perform(MockMvcRequestBuilders.post(BASE_PATH + "/uploads/" + fileUuid + "/submit"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SETTINGS1005"))
                .andExpect(jsonPath("$.data[0]").value(fileUuid.toString()));
    }

    @Test
    void uploadEmissionFactors_missingFile_returnsFileMissingError() throws Exception {
        AppUser appUser = AppUser.builder().userId("user1").roleType(RoleTypeConstants.REGULATOR).build();
        when(appSecurityComponent.getAuthenticatedUser()).thenReturn(appUser);
        when(settingsEmissionFactorsService.uploadEmissionFactors(eq(2026), isNull(), eq(appUser)))
                .thenThrow(new BusinessException(MetsErrorCode.EMISSION_FACTORS_FILE_MISSING));

        mockMvc.perform(MockMvcRequestBuilders.multipart(BASE_PATH)
                        .param("referenceYear", "2026")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SETTINGS1004"));
    }

    @Test
    void submitEmissionFactors_forbidden() throws Exception {
        AppUser appUser = AppUser.builder().userId("user1").roleType(RoleTypeConstants.REGULATOR).build();
        when(appSecurityComponent.getAuthenticatedUser()).thenReturn(appUser);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(appUserAuthorizationService)
                .authorize(appUser, "submitEmissionFactors");

        mockMvc.perform(MockMvcRequestBuilders.post(BASE_PATH + "/uploads/" + UUID.randomUUID() + "/submit"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(settingsEmissionFactorsService);
    }

    @Test
    void getEmissionFactorsHistory_returnsHistoryPage() throws Exception {
        AppUser appUser = AppUser.builder().userId("user1").roleType(RoleTypeConstants.REGULATOR).build();
        when(appSecurityComponent.getAuthenticatedUser()).thenReturn(appUser);
        when(settingsEmissionFactorsService.getEmissionFactorsHistory(0, 30)).thenReturn(
                EmissionFactorsHistoryResponseDTO.builder()
                        .history(List.of(EmissionFactorsHistoryRecordDTO.builder()
                                .changedAt(LocalDateTime.of(2026, 6, 21, 12, 20))
                                .changedBy("Jane Smith")
                                .referenceYear(2026)
                                .build()))
                        .totalItems(1)
                        .build());

        mockMvc.perform(MockMvcRequestBuilders.get(BASE_PATH + "/history").param("page", "0").param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.history[0].changedBy").value("Jane Smith"))
                .andExpect(jsonPath("$.history[0].referenceYear").value(2026));
    }

    @Test
    void getEmissionFactorsHistory_forbidden() throws Exception {
        AppUser appUser = AppUser.builder().userId("user1").roleType(RoleTypeConstants.REGULATOR).build();
        when(appSecurityComponent.getAuthenticatedUser()).thenReturn(appUser);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(appUserAuthorizationService)
                .authorize(appUser, "getEmissionFactorsHistory");

        mockMvc.perform(MockMvcRequestBuilders.get(BASE_PATH + "/history").param("page", "0").param("size", "30"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(settingsEmissionFactorsService);
    }

    @Test
    void generateGetEmissionFactorsFileToken_returnsToken() throws Exception {
        AppUser appUser = AppUser.builder().userId("user1").roleType(RoleTypeConstants.REGULATOR).build();
        when(appSecurityComponent.getAuthenticatedUser()).thenReturn(appUser);
        when(settingsEmissionFactorsService.generateGetEmissionFactorsFileToken(2026))
                .thenReturn(FileToken.builder().token("token123").build());

        mockMvc.perform(MockMvcRequestBuilders.get(BASE_PATH + "/2026/file"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token123"));

        verify(settingsEmissionFactorsService).generateGetEmissionFactorsFileToken(2026);
    }

    @Test
    void generateGetEmissionFactorsFileToken_notFound() throws Exception {
        AppUser appUser = AppUser.builder().userId("user1").roleType(RoleTypeConstants.REGULATOR).build();
        when(appSecurityComponent.getAuthenticatedUser()).thenReturn(appUser);
        when(settingsEmissionFactorsService.generateGetEmissionFactorsFileToken(2026))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(MockMvcRequestBuilders.get(BASE_PATH + "/2026/file"))
                .andExpect(status().isNotFound());
    }

    @Test
    void generateGetEmissionFactorsFileToken_forbidden() throws Exception {
        AppUser appUser = AppUser.builder().userId("user1").roleType(RoleTypeConstants.REGULATOR).build();
        when(appSecurityComponent.getAuthenticatedUser()).thenReturn(appUser);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(appUserAuthorizationService)
                .authorize(appUser, "generateGetEmissionFactorsFileToken");

        mockMvc.perform(MockMvcRequestBuilders.get(BASE_PATH + "/2026/file"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(settingsEmissionFactorsService);
    }
}
