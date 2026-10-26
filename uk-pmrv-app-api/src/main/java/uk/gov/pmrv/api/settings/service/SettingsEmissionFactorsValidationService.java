package uk.gov.pmrv.api.settings.service;

import java.time.LocalDate;
import java.time.Year;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import uk.gov.netz.api.common.exception.BusinessException;
import uk.gov.netz.api.files.common.domain.dto.FileDTO;
import uk.gov.pmrv.api.common.exception.MetsErrorCode;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsDetailDTO;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsNationalInventoryEntryDTO;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsRegionalNaturalGasEntryDTO;

@Service
public class SettingsEmissionFactorsValidationService {

    private static final String XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String FILENAME_PREFIX = "uk-ggi-carbon-emission-factors-and-calorific-values-";
    private static final String FILENAME_SUFFIX = ".xlsx";
    private static final String FILENAME_PATTERN = FILENAME_PREFIX + "\\d{4}" + FILENAME_SUFFIX;

    public void validateNotInAerReportingPeriod(Integer referenceYear) {
        if (!LocalDate.now().isBefore(LocalDate.of(referenceYear + 1, 1, 1))) {
            throw new BusinessException(MetsErrorCode.EMISSION_FACTORS_UPLOAD_IN_AER_PERIOD);
        }
    }

    public void validateNotFutureYear(Integer referenceYear) {
        if (referenceYear > Year.now().getValue()) {
            throw new BusinessException(MetsErrorCode.EMISSION_FACTORS_FILE_WRONG_YEAR);
        }
    }

    public void validateFilePresent(FileDTO file) {
        if (file == null || file.getFileContent() == null || file.getFileContent().length == 0) {
            throw new BusinessException(MetsErrorCode.EMISSION_FACTORS_FILE_MISSING);
        }
    }

    public void validateFileType(FileDTO file) {
        if (!XLSX_MIME_TYPE.equals(file.getFileType())) {
            throw new BusinessException(MetsErrorCode.EMISSION_FACTORS_FILE_NOT_XLSX);
        }
    }

    public void validateFileName(String fileName, Integer referenceYear) {
        if (!fileName.matches(FILENAME_PATTERN)) {
            throw new BusinessException(MetsErrorCode.EMISSION_FACTORS_FILE_NOT_XLSX);
        }
        String expected = FILENAME_PREFIX + referenceYear + FILENAME_SUFFIX;
        if (!expected.equals(fileName)) {
            throw new BusinessException(MetsErrorCode.EMISSION_FACTORS_FILE_WRONG_YEAR);
        }
    }

    public void validateParsedData(EmissionFactorsDetailDTO data) {
        data.getNationalInventory().forEach(this::validateNationalInventoryEntry);
        data.getRegionalNaturalGas().forEach(this::validateRegionalNaturalGasEntry);
        validateNoDuplicateNationalInventoryEntries(data.getNationalInventory());
        validateNoDuplicateRegionalNaturalGasEntries(data.getRegionalNaturalGas());
    }

    private void validateNoDuplicateNationalInventoryEntries(List<EmissionFactorsNationalInventoryEntryDTO> entries) {
        Set<String> seen = new HashSet<>();
        for (EmissionFactorsNationalInventoryEntryDTO entry : entries) {
            if (entry.getEmissionFactor() == null && entry.getNetCalorificValue() == null) {
                continue;
            }
            String key = entry.getSectorName() + "|" + entry.getFuel();
            if (!seen.add(key)) {
                throw new BusinessException(MetsErrorCode.EMISSION_FACTORS_DUPLICATE_NATIONAL_INVENTORY_ENTRY);
            }
        }
    }

    private void validateNoDuplicateRegionalNaturalGasEntries(List<EmissionFactorsRegionalNaturalGasEntryDTO> entries) {
        Set<String> seen = new HashSet<>();
        for (EmissionFactorsRegionalNaturalGasEntryDTO entry : entries) {
            if (!seen.add(entry.getChargingZoneCode())) {
                throw new BusinessException(MetsErrorCode.EMISSION_FACTORS_DUPLICATE_REGIONAL_GAS_ENTRY);
            }
        }
    }

    private void validateNationalInventoryEntry(EmissionFactorsNationalInventoryEntryDTO entry) {
        if (entry.getSectorName() == null || entry.getSectorName().isBlank()) {
            throw new BusinessException(MetsErrorCode.EMISSION_FACTORS_FILE_PARSE_ERROR);
        }
        boolean hasEf = entry.getEmissionFactor() != null;
        boolean hasNcv = entry.getNetCalorificValue() != null;
        boolean hasFuel = entry.getFuel() != null && !entry.getFuel().isBlank();
        boolean hasOxidationFactor = entry.getOxidationFactor() != null;
        boolean hasNcvUnit = entry.getNcvMeasurementUnit() != null;
        boolean complete = hasEf && hasNcv && hasFuel && hasOxidationFactor && hasNcvUnit;
        boolean empty = !hasEf && !hasNcv;
        if (!complete && !empty) {
            throw new BusinessException(MetsErrorCode.EMISSION_FACTORS_FILE_PARSE_ERROR);
        }
    }

    private void validateRegionalNaturalGasEntry(EmissionFactorsRegionalNaturalGasEntryDTO entry) {
        if (entry.getChargingZoneCode() == null || entry.getChargingZoneCode().isBlank()) {
            throw new BusinessException(MetsErrorCode.EMISSION_FACTORS_FILE_PARSE_ERROR);
        }
        boolean hasEf = entry.getEmissionFactor() != null;
        boolean hasNcv = entry.getNetCalorificValue() != null;
        if (hasEf != hasNcv) {
            throw new BusinessException(MetsErrorCode.EMISSION_FACTORS_FILE_PARSE_ERROR);
        }
    }
}
