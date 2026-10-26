package uk.gov.pmrv.api.settings.service;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.gov.netz.api.common.exception.BusinessException;
import uk.gov.netz.api.files.common.domain.dto.FileDTO;
import uk.gov.pmrv.api.common.exception.MetsErrorCode;
import uk.gov.pmrv.api.reporting.domain.NCVMeasurementUnit;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsDetailDTO;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsNationalInventoryEntryDTO;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsRegionalNaturalGasEntryDTO;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class SettingsEmissionFactorsValidationServiceTest {

    private final SettingsEmissionFactorsValidationService validationService = new SettingsEmissionFactorsValidationService();

    @Test
    void validateNotInAerReportingPeriod_currentYear_doesNotThrow() {
        int currentYear = Year.now().getValue();

        assertThatNoException().isThrownBy(() -> validationService.validateNotInAerReportingPeriod(currentYear));
    }

    @Test
    void validateNotInAerReportingPeriod_previousYear_throwsUploadInAerPeriod() {
        int previousYear = Year.now().getValue() - 1;

        assertThatThrownBy(() -> validationService.validateNotInAerReportingPeriod(previousYear))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_UPLOAD_IN_AER_PERIOD);
    }

    @Test
    void validateNotFutureYear_currentYear_doesNotThrow() {
        int currentYear = Year.now().getValue();

        assertThatNoException().isThrownBy(() -> validationService.validateNotFutureYear(currentYear));
    }

    @Test
    void validateNotFutureYear_nextYear_throwsFileWrongYear() {
        int nextYear = Year.now().getValue() + 1;

        assertThatThrownBy(() -> validationService.validateNotFutureYear(nextYear))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_FILE_WRONG_YEAR);
    }

    @Test
    void validateFilePresent_withContent_doesNotThrow() {
        FileDTO file = FileDTO.builder().fileName("file.xlsx").fileContent(new byte[]{1}).build();

        assertThatNoException().isThrownBy(() -> validationService.validateFilePresent(file));
    }

    @Test
    void validateFilePresent_noFile_throwsFileMissing() {
        assertThatThrownBy(() -> validationService.validateFilePresent(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_FILE_MISSING);
    }

    @Test
    void validateFilePresent_emptyFile_throwsFileMissing() {
        FileDTO file = FileDTO.builder().fileName("file.xlsx").fileContent(new byte[0]).build();

        assertThatThrownBy(() -> validationService.validateFilePresent(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_FILE_MISSING);
    }

    @Test
    void validateFileType_xlsxMimeType_doesNotThrow() {
        FileDTO file = FileDTO.builder()
                .fileName("uk-ggi-carbon-emission-factors-and-calorific-values-2026.xlsx")
                .fileType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .fileContent(new byte[0])
                .build();

        assertThatNoException().isThrownBy(() -> validationService.validateFileType(file));
    }

    @Test
    void validateFileType_invalidMimeType_throwsFileNotXlsx() {
        FileDTO file = FileDTO.builder()
                .fileName("uk-ggi-carbon-emission-factors-and-calorific-values-2026.pdf")
                .fileType("application/pdf")
                .fileContent(new byte[0])
                .build();

        assertThatThrownBy(() -> validationService.validateFileType(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_FILE_NOT_XLSX);
    }

    @Test
    void validateFileName_correctFileName_doesNotThrow() {
        int currentYear = Year.now().getValue();
        String fileName = "uk-ggi-carbon-emission-factors-and-calorific-values-" + currentYear + ".xlsx";

        assertThatNoException().isThrownBy(() -> validationService.validateFileName(fileName, currentYear));
    }

    @Test
    void validateFileName_wrongYear_throwsFileWrongYear() {
        int currentYear = Year.now().getValue();
        String fileName = "uk-ggi-carbon-emission-factors-and-calorific-values-" + (currentYear - 1) + ".xlsx";

        assertThatThrownBy(() -> validationService.validateFileName(fileName, currentYear))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_FILE_WRONG_YEAR);
    }

    @Test
    void validateFileName_wrongPrefix_throwsFileNotXlsx() {
        int currentYear = Year.now().getValue();
        String fileName = "emission-factors-" + currentYear + ".xlsx";

        assertThatThrownBy(() -> validationService.validateFileName(fileName, currentYear))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_FILE_NOT_XLSX);
    }

    @Test
    void validateFileName_wrongExtension_throwsFileNotXlsx() {
        int currentYear = Year.now().getValue();
        String fileName = "uk-ggi-carbon-emission-factors-and-calorific-values-" + currentYear + ".xls";

        assertThatThrownBy(() -> validationService.validateFileName(fileName, currentYear))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_FILE_NOT_XLSX);
    }

    @Test
    void validateParsedData_completeAndEmptyRows_doesNotThrow() {
        EmissionFactorsDetailDTO data = detail(
                List.of(completeNi("1A1a", "Coal"), emptyNi("1A1a", "Natural Gas"), emptyNi("1A1b", "Natural Gas")),
                List.of(completeRng("EA"), emptyRng("Stornoway")));

        assertThatNoException().isThrownBy(() -> validationService.validateParsedData(data));
    }

    @Test
    void validateParsedData_nationalRowWithoutNcv_throwsParseError() {
        EmissionFactorsNationalInventoryEntryDTO partial = completeNi("1A1a", "Coal");
        partial.setNetCalorificValue(null);
        EmissionFactorsDetailDTO data = detail(List.of(partial), List.of(completeRng("EA")));

        assertThatThrownBy(() -> validationService.validateParsedData(data))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_FILE_PARSE_ERROR);
    }

    @Test
    void validateParsedData_nationalRowWithoutFuel_throwsParseError() {
        EmissionFactorsDetailDTO data = detail(List.of(completeNi("1A1a", null)), List.of(completeRng("EA")));

        assertThatThrownBy(() -> validationService.validateParsedData(data))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_FILE_PARSE_ERROR);
    }

    @Test
    void validateParsedData_regionalRowWithoutNcv_throwsParseError() {
        EmissionFactorsRegionalNaturalGasEntryDTO partial = completeRng("EA");
        partial.setNetCalorificValue(null);
        EmissionFactorsDetailDTO data = detail(List.of(completeNi("1A1a", "Coal")), List.of(partial));

        assertThatThrownBy(() -> validationService.validateParsedData(data))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_FILE_PARSE_ERROR);
    }

    @Test
    void validateParsedData_duplicateSectorAndFuel_throwsDuplicateNationalInventoryEntry() {
        EmissionFactorsDetailDTO data = detail(
                List.of(completeNi("1A1a", "Coal"), completeNi("1A1a", "Coal")),
                List.of(completeRng("EA")));

        assertThatThrownBy(() -> validationService.validateParsedData(data))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_DUPLICATE_NATIONAL_INVENTORY_ENTRY);
    }

    @Test
    void validateParsedData_duplicateChargingZone_throwsDuplicateRegionalGasEntry() {
        EmissionFactorsDetailDTO data = detail(
                List.of(completeNi("1A1a", "Coal")),
                List.of(completeRng("EA"), completeRng("EA")));

        assertThatThrownBy(() -> validationService.validateParsedData(data))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_DUPLICATE_REGIONAL_GAS_ENTRY);
    }

    private EmissionFactorsDetailDTO detail(List<EmissionFactorsNationalInventoryEntryDTO> nationalInventory,
                                            List<EmissionFactorsRegionalNaturalGasEntryDTO> regionalNaturalGas) {
        return EmissionFactorsDetailDTO.builder()
                .nationalInventory(nationalInventory)
                .regionalNaturalGas(regionalNaturalGas)
                .build();
    }

    private EmissionFactorsNationalInventoryEntryDTO completeNi(String sectorName, String fuel) {
        return EmissionFactorsNationalInventoryEntryDTO.builder()
                .sectorName(sectorName)
                .fuel(fuel)
                .emissionFactor(BigDecimal.ONE)
                .netCalorificValue(BigDecimal.ONE)
                .oxidationFactor(BigDecimal.ONE)
                .ncvMeasurementUnit(NCVMeasurementUnit.GJ_PER_TONNE)
                .build();
    }

    private EmissionFactorsNationalInventoryEntryDTO emptyNi(String sectorName, String fuel) {
        return EmissionFactorsNationalInventoryEntryDTO.builder()
                .sectorName(sectorName)
                .fuel(fuel)
                .oxidationFactor(BigDecimal.ONE)
                .ncvMeasurementUnit(NCVMeasurementUnit.GJ_PER_TONNE)
                .build();
    }

    private EmissionFactorsRegionalNaturalGasEntryDTO completeRng(String chargingZoneCode) {
        return EmissionFactorsRegionalNaturalGasEntryDTO.builder()
                .chargingZoneCode(chargingZoneCode)
                .emissionFactor(BigDecimal.ONE)
                .netCalorificValue(BigDecimal.ONE)
                .build();
    }

    private EmissionFactorsRegionalNaturalGasEntryDTO emptyRng(String chargingZoneCode) {
        return EmissionFactorsRegionalNaturalGasEntryDTO.builder()
                .chargingZoneCode(chargingZoneCode)
                .build();
    }
}
