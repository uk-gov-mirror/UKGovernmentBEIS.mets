package uk.gov.pmrv.api.settings.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import uk.gov.pmrv.api.reporting.domain.EmissionCalculationParams;
import uk.gov.pmrv.api.reporting.domain.EmissionFactorMeasurementUnit;
import uk.gov.pmrv.api.reporting.domain.IpccSector;
import uk.gov.pmrv.api.reporting.domain.NCVMeasurementUnit;
import uk.gov.pmrv.api.reporting.domain.NationalInventoryData;
import uk.gov.pmrv.api.reporting.domain.RegionalInventoryData;
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
import uk.gov.pmrv.api.reporting.repository.ChargingZoneRepository;
import uk.gov.pmrv.api.settings.repository.EmissionFactorsRepository;
import uk.gov.pmrv.api.settings.service.EmissionFactorsExcelParserService.NationalInventoryRow;
import uk.gov.pmrv.api.settings.service.EmissionFactorsExcelParserService.ParsedEmissionFactorsData;
import uk.gov.pmrv.api.settings.service.EmissionFactorsExcelParserService.RegionalNaturalGasRow;
import uk.gov.pmrv.api.settings.transform.EmissionFactorsMapper;

@Service
@RequiredArgsConstructor
public class SettingsEmissionFactorsService {

    private static final BigDecimal REGIONAL_CALCULATION_FACTOR = new BigDecimal("0.9476");

    private final EmissionFactorsRepository emissionFactorsRepository;
    private final NationalInventoryDataRepository nationalInventoryDataRepository;
    private final RegionalInventoryDataRepository regionalInventoryDataRepository;
    private final IpccSectorRepository ipccSectorRepository;
    private final ChargingZoneRepository chargingZoneRepository;
    private final FileAttachmentService fileAttachmentService;
    private final FileAttachmentTokenService fileAttachmentTokenService;
    private final SettingsEmissionFactorsValidationService validationService;
    private final EmissionFactorsExcelParserService excelParserService;
    private final EmissionFactorsMapper emissionFactorsMapper;

    public List<EmissionFactorsHistoryEntryDTO> getEmissionFactors() {
        return emissionFactorsRepository.findLatestPerReferenceYear()
                .stream()
                .map(emissionFactorsMapper::toEmissionFactorsHistoryEntryDTO)
                .toList();
    }

    public EmissionFactorsDetailDTO getEmissionFactorsDetail(Integer referenceYear) {
        return emissionFactorsRepository.findLatestByReferenceYear(referenceYear)
                .map(EmissionFactorsHistoryEntry::getData)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * Parses the file and keeps it with its data as a pending upload, replacing any earlier pending upload for the year.
     * Nothing is applied until the returned upload is submitted, so the submitted values are exactly the previewed ones.
     */
    @Transactional(rollbackFor = Exception.class)
    public EmissionFactorsUploadDTO uploadEmissionFactors(Integer referenceYear, FileDTO file, AppUser appUser)
            throws IOException {
        EmissionFactorsDetailDTO data = validateAndParse(referenceYear, file);

        deletePendingUploads(referenceYear);

        String uuid = fileAttachmentService.createFileAttachment(file, FileStatus.PENDING, appUser.getUserId());
        emissionFactorsRepository.save(EmissionFactorsHistoryEntry.builder()
                .referenceYear(referenceYear)
                .fileUuid(UUID.fromString(uuid))
                .fileName(file.getFileName())
                .uploadedAt(LocalDateTime.now())
                .uploadedBy(appUser.getFullName())
                .userId(appUser.getUserId())
                .data(data)
                .status(EmissionFactorsHistoryEntryStatus.PENDING)
                .build());

        return EmissionFactorsUploadDTO.builder()
                .fileUuid(UUID.fromString(uuid))
                .data(data)
                .build();
    }

    /**
     * Applies the data stored with a pending upload, i.e. the data the user previewed, and completes the upload.
     */
    @Transactional
    public void submitEmissionFactors(UUID fileUuid, AppUser appUser) {
        EmissionFactorsHistoryEntry upload = findPendingUploadOfUser(fileUuid, appUser);
        validationService.validateNotInAerReportingPeriod(upload.getReferenceYear());
        validationService.validateNotFutureYear(upload.getReferenceYear());

        replaceInventoryData(Year.of(upload.getReferenceYear()), upload.getData());
        completeUpload(upload);
    }

    /**
     * Lists the submitted (completed) updates, newest first. Pending uploads are never part of the history.
     */
    @Transactional(readOnly = true)
    public EmissionFactorsHistoryResponseDTO getEmissionFactorsHistory(Integer page, Integer pageSize) {
        Page<EmissionFactorsHistoryEntry> historyPage = emissionFactorsRepository.findByStatus(
                EmissionFactorsHistoryEntryStatus.COMPLETED,
                PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "uploadedAt", "id")));

        return EmissionFactorsHistoryResponseDTO.builder()
                .history(historyPage.get()
                        .map(entry -> EmissionFactorsHistoryRecordDTO.builder()
                                .changedAt(entry.getUploadedAt())
                                .changedBy(entry.getUploadedBy())
                                .referenceYear(entry.getReferenceYear())
                                .build())
                        .toList())
                .totalItems(historyPage.getTotalElements())
                .build();
    }

    public FileToken generateGetEmissionFactorsFileToken(Integer referenceYear) {
        EmissionFactorsHistoryEntry entry = emissionFactorsRepository.findLatestByReferenceYear(referenceYear)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        return fileAttachmentTokenService.generateGetFileAttachmentToken(entry.getFileUuid().toString());
    }

    private void deletePendingUploads(Integer referenceYear) {
        List<EmissionFactorsHistoryEntry> pendingUploads =
                emissionFactorsRepository.findByReferenceYearAndStatus(referenceYear, EmissionFactorsHistoryEntryStatus.PENDING);
        if (pendingUploads.isEmpty()) {
            return;
        }
        fileAttachmentService.deleteFileAttachments(pendingUploads.stream()
                .map(entry -> entry.getFileUuid().toString())
                .collect(Collectors.toSet()));
        emissionFactorsRepository.deleteAll(pendingUploads);
    }

    private EmissionFactorsHistoryEntry findPendingUploadOfUser(UUID fileUuid, AppUser appUser) {
        return emissionFactorsRepository.findByFileUuid(fileUuid)
                .filter(entry -> entry.getStatus() == EmissionFactorsHistoryEntryStatus.PENDING)
                .filter(entry -> entry.getUserId().equals(appUser.getUserId()))
                .orElseThrow(() -> new BusinessException(MetsErrorCode.EMISSION_FACTORS_UPLOAD_NOT_FOUND, fileUuid));
    }

    /**
     * Replaces the year's national and regional inventory data. The new rows are built before anything is deleted,
     * so invalid data fails the submission without touching the existing data.
     */
    private void replaceInventoryData(Year year, EmissionFactorsDetailDTO data) {
        List<NationalInventoryData> nationalData = toNationalInventoryData(data.getNationalInventory(), year);
        List<RegionalInventoryData> regionalData = toRegionalInventoryData(data.getRegionalNaturalGas(), year);

        nationalInventoryDataRepository.deleteByReportingYear(year);
        regionalInventoryDataRepository.deleteByReportingYear(year);
        nationalInventoryDataRepository.saveAll(nationalData);
        regionalInventoryDataRepository.saveAll(regionalData);
    }

    private List<NationalInventoryData> toNationalInventoryData(List<EmissionFactorsNationalInventoryEntryDTO> entries,
                                                                Year year) {
        Map<String, IpccSector> sectorsByName = ipccSectorRepository.findAll().stream()
                .collect(Collectors.toMap(IpccSector::getName, Function.identity()));

        return entries.stream()
                .filter(dto -> dto.getEmissionFactor() != null && dto.getNetCalorificValue() != null)
                .map(dto -> buildNationalInventoryData(dto, year, sectorsByName))
                .toList();
    }

    private List<RegionalInventoryData> toRegionalInventoryData(List<EmissionFactorsRegionalNaturalGasEntryDTO> entries,
                                                                Year year) {
        Map<String, ChargingZone> zonesByCode = chargingZoneRepository.findAll().stream()
                .collect(Collectors.toMap(ChargingZone::getCode, Function.identity()));

        return entries.stream()
                .filter(dto -> dto.getEmissionFactor() != null && dto.getNetCalorificValue() != null)
                .map(dto -> buildRegionalInventoryData(dto, year, zonesByCode))
                .toList();
    }

    /**
     * Marks the upload as completed; uploaded_at records when the whole action was completed.
     */
    private void completeUpload(EmissionFactorsHistoryEntry upload) {
        fileAttachmentService.updateFileAttachmentStatus(upload.getFileUuid().toString(), FileStatus.SUBMITTED);
        upload.setStatus(EmissionFactorsHistoryEntryStatus.COMPLETED);
        upload.setUploadedAt(LocalDateTime.now());
        emissionFactorsRepository.save(upload);
    }

    private EmissionFactorsDetailDTO validateAndParse(Integer referenceYear, FileDTO file) {
        validationService.validateFilePresent(file);
        validationService.validateNotInAerReportingPeriod(referenceYear);
        validationService.validateNotFutureYear(referenceYear);
        validationService.validateFileType(file);
        validationService.validateFileName(file.getFileName(), referenceYear);
        ParsedEmissionFactorsData parsed = excelParserService.parse(file.getFileContent());

        Map<String, String> zoneNames = chargingZoneRepository.findAll().stream()
                .collect(Collectors.toMap(ChargingZone::getCode, ChargingZone::getName));

        EmissionFactorsDetailDTO data = EmissionFactorsDetailDTO.builder()
                .nationalInventory(parsed.nationalInventory().stream()
                        .map(this::toNationalInventoryEntryDTO)
                        .toList())
                .regionalNaturalGas(parsed.regionalNaturalGas().stream()
                        .map(row -> toRegionalNaturalGasEntryDTO(row, zoneNames))
                        .toList())
                .build();
        validationService.validateParsedData(data);
        return data;
    }

    private EmissionFactorsNationalInventoryEntryDTO toNationalInventoryEntryDTO(NationalInventoryRow row) {
        return EmissionFactorsNationalInventoryEntryDTO.builder()
                .sectorName(row.sectorCode())
                .ukGhgFuel(row.ukGhgFuel())
                .fuel(row.fuel())
                .emissionFactor(row.emissionFactor())
                .netCalorificValue(row.netCalorificValue())
                .oxidationFactor(row.oxidationFactor())
                .ncvMeasurementUnit(row.ncvMeasurementUnit())
                .efMeasurementUnit(EmissionFactorMeasurementUnit.TONNES_OF_CO2_PER_TJ)
                .build();
    }

    private EmissionFactorsRegionalNaturalGasEntryDTO toRegionalNaturalGasEntryDTO(RegionalNaturalGasRow row,
                                                                                    Map<String, String> zoneNames) {
        return EmissionFactorsRegionalNaturalGasEntryDTO.builder()
                .chargingZoneCode(row.chargingZoneCode())
                .chargingZoneName(zoneNames.get(row.chargingZoneCode()))
                .emissionFactor(row.emissionFactor())
                .netCalorificValue(row.netCalorificValue())
                .calculationFactor(REGIONAL_CALCULATION_FACTOR)
                .build();
    }

    private NationalInventoryData buildNationalInventoryData(EmissionFactorsNationalInventoryEntryDTO dto, Year year,
                                                              Map<String, IpccSector> sectorsByName) {
        IpccSector sector = sectorsByName.get(dto.getSectorName());
        if (sector == null) {
            throw new BusinessException(MetsErrorCode.EMISSION_FACTORS_FILE_PARSE_ERROR);
        }
        return NationalInventoryData.builder()
                .reportingYear(year)
                .sector(sector)
                .fuel(dto.getFuel())
                .emissionCalculationParams(EmissionCalculationParams.builder()
                        .emissionFactor(dto.getEmissionFactor())
                        .netCalorificValue(dto.getNetCalorificValue())
                        .oxidationFactor(dto.getOxidationFactor())
                        .ncvMeasurementUnit(dto.getNcvMeasurementUnit())
                        .efMeasurementUnit(EmissionFactorMeasurementUnit.TONNES_OF_CO2_PER_TJ)
                        .build())
                .build();
    }

    private RegionalInventoryData buildRegionalInventoryData(EmissionFactorsRegionalNaturalGasEntryDTO dto, Year year,
                                                              Map<String, ChargingZone> zonesByCode) {
        ChargingZone zone = zonesByCode.get(dto.getChargingZoneCode());
        if (zone == null) {
            throw new BusinessException(MetsErrorCode.EMISSION_FACTORS_FILE_PARSE_ERROR);
        }
        return RegionalInventoryData.builder()
                .reportingYear(year)
                .chargingZone(zone)
                .emissionCalculationParams(EmissionCalculationParams.builder()
                        .emissionFactor(dto.getEmissionFactor())
                        .netCalorificValue(dto.getNetCalorificValue())
                        .oxidationFactor(BigDecimal.ONE)
                        .ncvMeasurementUnit(NCVMeasurementUnit.GJ_PER_NM3)
                        .efMeasurementUnit(EmissionFactorMeasurementUnit.TONNES_OF_CO2_PER_TJ)
                        .build())
                .calculationFactor(REGIONAL_CALCULATION_FACTOR)
                .build();
    }
}
