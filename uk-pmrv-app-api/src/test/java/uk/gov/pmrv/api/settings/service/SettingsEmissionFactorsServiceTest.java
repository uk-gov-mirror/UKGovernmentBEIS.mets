package uk.gov.pmrv.api.settings.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.netz.api.authorization.core.domain.AppUser;
import uk.gov.netz.api.common.exception.BusinessException;
import uk.gov.netz.api.common.exception.ErrorCode;
import uk.gov.pmrv.api.common.exception.MetsErrorCode;
import uk.gov.netz.api.files.attachments.service.FileAttachmentService;
import uk.gov.netz.api.files.attachments.service.FileAttachmentTokenService;
import uk.gov.netz.api.files.common.domain.FileStatus;
import uk.gov.netz.api.files.common.domain.dto.FileDTO;
import uk.gov.netz.api.token.FileToken;
import uk.gov.pmrv.api.reporting.domain.ChargingZone;
import uk.gov.pmrv.api.reporting.domain.IpccSector;
import uk.gov.pmrv.api.reporting.domain.NCVMeasurementUnit;
import uk.gov.pmrv.api.reporting.domain.NationalInventoryData;
import uk.gov.pmrv.api.reporting.domain.RegionalInventoryData;
import uk.gov.pmrv.api.reporting.repository.ChargingZoneRepository;
import uk.gov.pmrv.api.reporting.repository.IpccSectorRepository;
import uk.gov.pmrv.api.reporting.repository.NationalInventoryDataRepository;
import uk.gov.pmrv.api.reporting.repository.RegionalInventoryDataRepository;
import uk.gov.pmrv.api.settings.domain.EmissionFactorsHistoryEntry;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsDetailDTO;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsHistoryEntryDTO;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsHistoryRecordDTO;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsHistoryResponseDTO;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsNationalInventoryEntryDTO;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsRegionalNaturalGasEntryDTO;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsUploadDTO;
import uk.gov.pmrv.api.settings.domain.enumeration.EmissionFactorsHistoryEntryStatus;
import uk.gov.pmrv.api.settings.repository.EmissionFactorsRepository;
import uk.gov.pmrv.api.settings.service.EmissionFactorsExcelParserService.NationalInventoryRow;
import uk.gov.pmrv.api.settings.service.EmissionFactorsExcelParserService.ParsedEmissionFactorsData;
import uk.gov.pmrv.api.settings.service.EmissionFactorsExcelParserService.RegionalNaturalGasRow;
import uk.gov.pmrv.api.settings.transform.EmissionFactorsMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsEmissionFactorsServiceTest {

    @InjectMocks
    private SettingsEmissionFactorsService service;

    @Mock
    private EmissionFactorsRepository emissionFactorsRepository;

    @Mock
    private NationalInventoryDataRepository nationalInventoryDataRepository;

    @Mock
    private RegionalInventoryDataRepository regionalInventoryDataRepository;

    @Mock
    private IpccSectorRepository ipccSectorRepository;

    @Mock
    private ChargingZoneRepository chargingZoneRepository;

    @Mock
    private FileAttachmentService fileAttachmentService;

    @Mock
    private FileAttachmentTokenService fileAttachmentTokenService;

    @Mock
    private SettingsEmissionFactorsValidationService validationService;

    @Mock
    private EmissionFactorsExcelParserService excelParserService;

    @Mock
    private EmissionFactorsMapper emissionFactorsMapper;

    private final AppUser appUser = AppUser.builder().userId("user1").firstName("Jane").lastName("Smith").build();

    @Test
    void getEmissionFactors_returnsMappedDTOsSortedByRepo() {
        EmissionFactorsHistoryEntry entry2024 = entry(2024);
        EmissionFactorsHistoryEntry entry2023 = entry(2023);
        EmissionFactorsHistoryEntryDTO dto2024 = dto(2024);
        EmissionFactorsHistoryEntryDTO dto2023 = dto(2023);

        when(emissionFactorsRepository.findLatestPerReferenceYear()).thenReturn(List.of(entry2024, entry2023));
        when(emissionFactorsMapper.toEmissionFactorsHistoryEntryDTO(entry2024)).thenReturn(dto2024);
        when(emissionFactorsMapper.toEmissionFactorsHistoryEntryDTO(entry2023)).thenReturn(dto2023);

        List<EmissionFactorsHistoryEntryDTO> result = service.getEmissionFactors();

        assertThat(result).containsExactly(dto2024, dto2023);
    }

    @Test
    void getEmissionFactors_noEntries_returnsEmptyList() {
        when(emissionFactorsRepository.findLatestPerReferenceYear()).thenReturn(List.of());

        assertThat(service.getEmissionFactors()).isEmpty();
    }

    @Test
    void getEmissionFactorsDetail_entryFound_returnsSubmittedData() {
        EmissionFactorsDetailDTO data = EmissionFactorsDetailDTO.builder()
                .nationalInventory(List.of(EmissionFactorsNationalInventoryEntryDTO.builder()
                        .sectorName("1A1a").ukGhgFuel("Fuel Oil [note 8]").fuel("Fuel Oil").build()))
                .regionalNaturalGas(List.of(EmissionFactorsRegionalNaturalGasEntryDTO.builder()
                        .chargingZoneCode("EA").chargingZoneName("Eastern").build()))
                .build();
        EmissionFactorsHistoryEntry historyEntry = EmissionFactorsHistoryEntry.builder()
                .id(1L)
                .referenceYear(2026)
                .fileUuid(UUID.randomUUID())
                .fileName("uk-ggi-carbon-emission-factors-and-calorific-values-2026.xlsx")
                .uploadedAt(LocalDateTime.now())
                .uploadedBy("Jane Smith")
                .userId("user1")
                .data(data)
                .build();

        when(emissionFactorsRepository.findLatestByReferenceYear(2026)).thenReturn(Optional.of(historyEntry));

        assertThat(service.getEmissionFactorsDetail(2026)).isEqualTo(data);
        verifyNoInteractions(nationalInventoryDataRepository, regionalInventoryDataRepository);
    }

    @Test
    void getEmissionFactorsDetail_entryWithoutData_throwsResourceNotFound() {
        when(emissionFactorsRepository.findLatestByReferenceYear(2026))
                .thenReturn(Optional.of(entry(2026, EmissionFactorsHistoryEntryStatus.COMPLETED, "user1", null)));

        assertThatThrownBy(() -> service.getEmissionFactorsDetail(2026))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void getEmissionFactorsDetail_entryNotFound_throwsResourceNotFound() {
        when(emissionFactorsRepository.findLatestByReferenceYear(2026)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEmissionFactorsDetail(2026))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void uploadEmissionFactors_savesPendingUploadWithParsedData() throws IOException {
        FileDTO file = xlsxFile(2026);
        UUID fileUuid = UUID.randomUUID();

        when(excelParserService.parse(file.getFileContent())).thenReturn(parsedData());
        when(chargingZoneRepository.findAll()).thenReturn(List.of(zone()));
        when(emissionFactorsRepository.findByReferenceYearAndStatus(2026, EmissionFactorsHistoryEntryStatus.PENDING))
                .thenReturn(List.of());
        when(fileAttachmentService.createFileAttachment(file, FileStatus.PENDING, "user1"))
                .thenReturn(fileUuid.toString());

        EmissionFactorsUploadDTO result = service.uploadEmissionFactors(2026, file, appUser);

        InOrder inOrder = inOrder(validationService);
        inOrder.verify(validationService).validateFilePresent(file);
        inOrder.verify(validationService).validateNotInAerReportingPeriod(2026);
        inOrder.verify(validationService).validateNotFutureYear(2026);
        inOrder.verify(validationService).validateFileType(file);
        inOrder.verify(validationService).validateFileName(file.getFileName(), 2026);
        inOrder.verify(validationService).validateParsedData(result.getData());
        verify(fileAttachmentService, never()).deleteFileAttachments(any());
        verifyNoInteractions(nationalInventoryDataRepository, regionalInventoryDataRepository, ipccSectorRepository);

        ArgumentCaptor<EmissionFactorsHistoryEntry> entryCaptor = ArgumentCaptor.forClass(EmissionFactorsHistoryEntry.class);
        verify(emissionFactorsRepository).save(entryCaptor.capture());
        EmissionFactorsHistoryEntry saved = entryCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(EmissionFactorsHistoryEntryStatus.PENDING);
        assertThat(saved.getReferenceYear()).isEqualTo(2026);
        assertThat(saved.getFileUuid()).isEqualTo(fileUuid);
        assertThat(saved.getUploadedBy()).isEqualTo("Jane Smith");
        assertThat(saved.getUserId()).isEqualTo("user1");
        assertThat(saved.getData()).isEqualTo(result.getData());

        assertThat(result.getFileUuid()).isEqualTo(fileUuid);
        EmissionFactorsNationalInventoryEntryDTO ni = result.getData().getNationalInventory().getFirst();
        assertThat(ni.getUkGhgFuel()).isEqualTo("Fuel Oil [note 8]");
        assertThat(ni.getFuel()).isEqualTo("Fuel Oil");
        assertThat(result.getData().getRegionalNaturalGas().getFirst().getChargingZoneName()).isEqualTo("Eastern");
    }

    @Test
    void uploadEmissionFactors_replacesEarlierPendingUploadsOfTheYear() throws IOException {
        FileDTO file = xlsxFile(2026);
        EmissionFactorsHistoryEntry earlierUpload = entry(2026, EmissionFactorsHistoryEntryStatus.PENDING, "user2");

        when(excelParserService.parse(file.getFileContent())).thenReturn(parsedData());
        when(chargingZoneRepository.findAll()).thenReturn(List.of(zone()));
        when(emissionFactorsRepository.findByReferenceYearAndStatus(2026, EmissionFactorsHistoryEntryStatus.PENDING))
                .thenReturn(List.of(earlierUpload));
        when(fileAttachmentService.createFileAttachment(file, FileStatus.PENDING, "user1"))
                .thenReturn(UUID.randomUUID().toString());

        service.uploadEmissionFactors(2026, file, appUser);

        verify(fileAttachmentService).deleteFileAttachments(Set.of(earlierUpload.getFileUuid().toString()));
        verify(emissionFactorsRepository).deleteAll(List.of(earlierUpload));
    }

    @Test
    void uploadEmissionFactors_invalidFile_savesNothingAndKeepsPendingUploads() {
        FileDTO file = xlsxFile(2026);
        doThrow(new BusinessException(MetsErrorCode.EMISSION_FACTORS_FILE_MISSING))
                .when(validationService).validateFilePresent(file);

        assertThatThrownBy(() -> service.uploadEmissionFactors(2026, file, appUser))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_FILE_MISSING);

        verifyNoInteractions(excelParserService, emissionFactorsRepository, fileAttachmentService);
    }

    @Test
    void submitEmissionFactors_appliesThePreviewedDataAndCompletesTheUpload() {
        Year year = Year.of(2026);
        EmissionFactorsHistoryEntry upload = entry(2026, EmissionFactorsHistoryEntryStatus.PENDING, "user1");
        IpccSector sector = IpccSector.builder().id(1L).name("1A1a").displayName("Public Electricity").build();
        ChargingZone zone = zone();

        when(emissionFactorsRepository.findByFileUuid(upload.getFileUuid())).thenReturn(Optional.of(upload));
        when(ipccSectorRepository.findAll()).thenReturn(List.of(sector));
        when(chargingZoneRepository.findAll()).thenReturn(List.of(zone));

        LocalDateTime beforeSubmit = LocalDateTime.now();
        service.submitEmissionFactors(upload.getFileUuid(), appUser);

        verify(validationService).validateNotInAerReportingPeriod(2026);
        verify(validationService).validateNotFutureYear(2026);
        verify(nationalInventoryDataRepository).deleteByReportingYear(year);
        verify(regionalInventoryDataRepository).deleteByReportingYear(year);
        verifyNoInteractions(excelParserService);

        ArgumentCaptor<List<NationalInventoryData>> niCaptor = ArgumentCaptor.forClass(List.class);
        verify(nationalInventoryDataRepository).saveAll(niCaptor.capture());
        assertThat(niCaptor.getValue()).singleElement().satisfies(ni -> {
            assertThat(ni.getSector()).isEqualTo(sector);
            assertThat(ni.getFuel()).isEqualTo("Fuel Oil");
            assertThat(ni.getEmissionCalculationParams().getEmissionFactor()).isEqualByComparingTo(BigDecimal.valueOf(93.68));
        });

        ArgumentCaptor<List<RegionalInventoryData>> rngCaptor = ArgumentCaptor.forClass(List.class);
        verify(regionalInventoryDataRepository).saveAll(rngCaptor.capture());
        assertThat(rngCaptor.getValue()).singleElement().satisfies(rng -> {
            assertThat(rng.getChargingZone()).isEqualTo(zone);
            assertThat(rng.getEmissionCalculationParams().getEmissionFactor()).isEqualByComparingTo(BigDecimal.valueOf(56.39));
        });

        verify(fileAttachmentService).updateFileAttachmentStatus(upload.getFileUuid().toString(), FileStatus.SUBMITTED);
        verify(emissionFactorsRepository).save(upload);
        assertThat(upload.getStatus()).isEqualTo(EmissionFactorsHistoryEntryStatus.COMPLETED);
        assertThat(upload.getUploadedAt()).isAfterOrEqualTo(beforeSubmit);
    }

    @Test
    void submitEmissionFactors_rowsWithoutValues_areNotSavedAsLiveData() {
        EmissionFactorsDetailDTO data = EmissionFactorsDetailDTO.builder()
                .nationalInventory(List.of(EmissionFactorsNationalInventoryEntryDTO.builder()
                        .sectorName("1A1a").ukGhgFuel("Natural Gas").fuel("Natural Gas").build()))
                .regionalNaturalGas(List.of(EmissionFactorsRegionalNaturalGasEntryDTO.builder()
                        .chargingZoneCode("Stornoway").build()))
                .build();
        EmissionFactorsHistoryEntry upload = entry(2026, EmissionFactorsHistoryEntryStatus.PENDING, "user1", data);

        when(emissionFactorsRepository.findByFileUuid(upload.getFileUuid())).thenReturn(Optional.of(upload));

        service.submitEmissionFactors(upload.getFileUuid(), appUser);

        verify(nationalInventoryDataRepository).saveAll(List.of());
        verify(regionalInventoryDataRepository).saveAll(List.of());
        assertThat(upload.getStatus()).isEqualTo(EmissionFactorsHistoryEntryStatus.COMPLETED);
    }

    @Test
    void submitEmissionFactors_uploadReplacedOrMissing_throwsUploadNotFound() {
        UUID fileUuid = UUID.randomUUID();
        when(emissionFactorsRepository.findByFileUuid(fileUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitEmissionFactors(fileUuid, appUser))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    assertThat(((BusinessException) e).getErrorCode()).isEqualTo(MetsErrorCode.EMISSION_FACTORS_UPLOAD_NOT_FOUND);
                    assertThat(((BusinessException) e).getData()).containsExactly(fileUuid);
                });

        verifyNoInteractions(nationalInventoryDataRepository, regionalInventoryDataRepository, fileAttachmentService);
    }

    @Test
    void submitEmissionFactors_alreadySubmitted_throwsUploadNotFound() {
        EmissionFactorsHistoryEntry upload = entry(2026, EmissionFactorsHistoryEntryStatus.COMPLETED, "user1");
        when(emissionFactorsRepository.findByFileUuid(upload.getFileUuid())).thenReturn(Optional.of(upload));

        assertThatThrownBy(() -> service.submitEmissionFactors(upload.getFileUuid(), appUser))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_UPLOAD_NOT_FOUND);

        verifyNoInteractions(nationalInventoryDataRepository, regionalInventoryDataRepository, fileAttachmentService);
    }

    @Test
    void submitEmissionFactors_uploadedByAnotherUser_throwsUploadNotFound() {
        EmissionFactorsHistoryEntry upload = entry(2026, EmissionFactorsHistoryEntryStatus.PENDING, "user2");
        when(emissionFactorsRepository.findByFileUuid(upload.getFileUuid())).thenReturn(Optional.of(upload));

        assertThatThrownBy(() -> service.submitEmissionFactors(upload.getFileUuid(), appUser))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_UPLOAD_NOT_FOUND);

        assertThat(upload.getStatus()).isEqualTo(EmissionFactorsHistoryEntryStatus.PENDING);
        verifyNoInteractions(nationalInventoryDataRepository, regionalInventoryDataRepository, fileAttachmentService);
    }

    @Test
    void submitEmissionFactors_unknownSectorCode_throwsParseErrorAndSavesNothing() {
        EmissionFactorsHistoryEntry upload = entry(2026, EmissionFactorsHistoryEntryStatus.PENDING, "user1");
        when(emissionFactorsRepository.findByFileUuid(upload.getFileUuid())).thenReturn(Optional.of(upload));
        when(ipccSectorRepository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> service.submitEmissionFactors(upload.getFileUuid(), appUser))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_FILE_PARSE_ERROR);

        verify(nationalInventoryDataRepository, never()).deleteByReportingYear(any());
        verifyNoInteractions(fileAttachmentService);
        assertThat(upload.getStatus()).isEqualTo(EmissionFactorsHistoryEntryStatus.PENDING);
    }

    @Test
    void getEmissionFactorsHistory_returnsCompletedUpdatesNewestFirstWithTheStoredNames() {
        LocalDateTime completedAt = LocalDateTime.of(2026, 6, 21, 12, 20);
        EmissionFactorsHistoryEntry latest = historyEntry(5L, 2026, completedAt, "Jane Smith");
        EmissionFactorsHistoryEntry earlier = historyEntry(4L, 2026, completedAt.minusDays(3), "Jane Smith");
        EmissionFactorsHistoryEntry otherUser = historyEntry(3L, 2026, completedAt.minusDays(9), "John Brown");
        PageRequest pageRequest = PageRequest.of(1, 30, Sort.by(Sort.Direction.DESC, "uploadedAt", "id"));
        when(emissionFactorsRepository.findByStatus(EmissionFactorsHistoryEntryStatus.COMPLETED, pageRequest))
                .thenReturn(new PageImpl<>(List.of(latest, earlier, otherUser), pageRequest, 33));

        EmissionFactorsHistoryResponseDTO result = service.getEmissionFactorsHistory(1, 30);

        assertThat(result.getTotalItems()).isEqualTo(33);
        assertThat(result.getHistory())
                .extracting(EmissionFactorsHistoryRecordDTO::getChangedAt, EmissionFactorsHistoryRecordDTO::getChangedBy,
                        EmissionFactorsHistoryRecordDTO::getReferenceYear)
                .containsExactly(
                        tuple(completedAt, "Jane Smith", 2026),
                        tuple(completedAt.minusDays(3), "Jane Smith", 2026),
                        tuple(completedAt.minusDays(9), "John Brown", 2026));
    }

    @Test
    void getEmissionFactorsHistory_noHistory_returnsEmptyPage() {
        PageRequest pageRequest = PageRequest.of(0, 30, Sort.by(Sort.Direction.DESC, "uploadedAt", "id"));
        when(emissionFactorsRepository.findByStatus(EmissionFactorsHistoryEntryStatus.COMPLETED, pageRequest))
                .thenReturn(Page.empty(pageRequest));

        EmissionFactorsHistoryResponseDTO result = service.getEmissionFactorsHistory(0, 30);

        assertThat(result.getHistory()).isEmpty();
        assertThat(result.getTotalItems()).isZero();
    }

    @Test
    void generateGetEmissionFactorsFileToken_entryFound_returnsToken() {
        EmissionFactorsHistoryEntry historyEntry = entry(2026);
        FileToken expectedToken = FileToken.builder().token("token123").build();

        when(emissionFactorsRepository.findLatestByReferenceYear(2026)).thenReturn(Optional.of(historyEntry));
        when(fileAttachmentTokenService.generateGetFileAttachmentToken(historyEntry.getFileUuid().toString()))
                .thenReturn(expectedToken);

        FileToken result = service.generateGetEmissionFactorsFileToken(2026);

        assertThat(result).isEqualTo(expectedToken);
    }

    @Test
    void generateGetEmissionFactorsFileToken_entryNotFound_throwsResourceNotFound() {
        when(emissionFactorsRepository.findLatestByReferenceYear(2026)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateGetEmissionFactorsFileToken(2026))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private EmissionFactorsHistoryEntry entry(int year) {
        return entry(year, EmissionFactorsHistoryEntryStatus.COMPLETED, "user1");
    }

    private EmissionFactorsHistoryEntry entry(int year, EmissionFactorsHistoryEntryStatus status, String userId) {
        return entry(year, status, userId, detail());
    }

    private EmissionFactorsHistoryEntry entry(int year, EmissionFactorsHistoryEntryStatus status, String userId,
                                       EmissionFactorsDetailDTO data) {
        return EmissionFactorsHistoryEntry.builder()
                .id((long) year)
                .referenceYear(year)
                .fileUuid(UUID.randomUUID())
                .fileName("uk-ggi-carbon-emission-factors-and-calorific-values-" + year + ".xlsx")
                .uploadedAt(LocalDateTime.now())
                .uploadedBy("Jane Smith")
                .userId(userId)
                .data(data)
                .status(status)
                .build();
    }

    private EmissionFactorsHistoryEntry historyEntry(Long id, int year, LocalDateTime completedAt, String uploadedBy) {
        return EmissionFactorsHistoryEntry.builder()
                .id(id)
                .referenceYear(year)
                .fileUuid(UUID.randomUUID())
                .fileName("uk-ggi-carbon-emission-factors-and-calorific-values-" + year + ".xlsx")
                .uploadedAt(completedAt)
                .uploadedBy(uploadedBy)
                .userId("user1")
                .status(EmissionFactorsHistoryEntryStatus.COMPLETED)
                .build();
    }

    private EmissionFactorsDetailDTO detail() {
        return EmissionFactorsDetailDTO.builder()
                .nationalInventory(List.of(EmissionFactorsNationalInventoryEntryDTO.builder()
                        .sectorName("1A1a")
                        .ukGhgFuel("Fuel Oil [note 8]")
                        .fuel("Fuel Oil")
                        .emissionFactor(BigDecimal.valueOf(93.68))
                        .netCalorificValue(BigDecimal.valueOf(23.62))
                        .oxidationFactor(BigDecimal.valueOf(0.9827))
                        .ncvMeasurementUnit(NCVMeasurementUnit.GJ_PER_TONNE)
                        .build()))
                .regionalNaturalGas(List.of(EmissionFactorsRegionalNaturalGasEntryDTO.builder()
                        .chargingZoneCode("EA")
                        .emissionFactor(BigDecimal.valueOf(56.39))
                        .netCalorificValue(BigDecimal.valueOf(0.03739))
                        .build()))
                .build();
    }

    private ParsedEmissionFactorsData parsedData() {
        NationalInventoryRow niRow = new NationalInventoryRow(
                "1A1a", "Fuel Oil [note 8]", "Fuel Oil",
                BigDecimal.valueOf(93.68), BigDecimal.valueOf(23.62),
                BigDecimal.valueOf(0.9827), NCVMeasurementUnit.GJ_PER_TONNE);
        RegionalNaturalGasRow rngRow = new RegionalNaturalGasRow(
                "EA", BigDecimal.valueOf(56.39), BigDecimal.valueOf(0.03739));
        return new ParsedEmissionFactorsData(List.of(niRow), List.of(rngRow));
    }

    private ChargingZone zone() {
        return ChargingZone.builder().id(1L).code("EA").name("Eastern").build();
    }

    private EmissionFactorsHistoryEntryDTO dto(int year) {
        return EmissionFactorsHistoryEntryDTO.builder()
                .referenceYear(year)
                .fileUuid(UUID.randomUUID())
                .fileName("uk-ggi-carbon-emission-factors-and-calorific-values-" + year + ".xlsx")
                .uploadedAt(LocalDate.now())
                .build();
    }

    private FileDTO xlsxFile(int year) {
        return FileDTO.builder()
                .fileName("uk-ggi-carbon-emission-factors-and-calorific-values-" + year + ".xlsx")
                .fileType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .fileContent(new byte[0])
                .build();
    }
}
