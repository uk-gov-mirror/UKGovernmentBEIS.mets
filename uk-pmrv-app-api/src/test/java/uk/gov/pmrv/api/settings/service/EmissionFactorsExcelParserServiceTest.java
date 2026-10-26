package uk.gov.pmrv.api.settings.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import uk.gov.netz.api.common.exception.BusinessException;
import uk.gov.pmrv.api.common.exception.MetsErrorCode;
import uk.gov.pmrv.api.reporting.domain.NCVMeasurementUnit;
import uk.gov.pmrv.api.settings.service.EmissionFactorsExcelParserService.NationalInventoryRow;
import uk.gov.pmrv.api.settings.service.EmissionFactorsExcelParserService.ParsedEmissionFactorsData;
import uk.gov.pmrv.api.settings.service.EmissionFactorsExcelParserService.RegionalNaturalGasRow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class EmissionFactorsExcelParserServiceTest {

    private static final String NATIONAL_INVENTORY_SHEET = "Emission_Factors_&_CVs";
    private static final String REGIONAL_NATURAL_GAS_SHEET = "Regional_Natural_Gas_Data";
    private static final int DATA_START_ROW_INDEX = 5;
    private static final int NATIONAL_INVENTORY_ROWS = 67;
    private static final List<String> REGIONAL_ZONE_CODES = List.of(
            "EA", "EM", "NE", "NO", "NT", "NW", "SC", "SE", "SO", "SW", "WM", "WN", "WS", "SIU", "Stornoway", "Stranraer");

    private final EmissionFactorsExcelParserService parser = new EmissionFactorsExcelParserService();

    private record NationalRow(String crtCategory, String industry, String fuel,
                               Object emissionFactor, Object ncv, String ncvUnit, Object oxidationFactor) {}

    private record RegionalRow(Object zoneCode, Object emissionFactor, Object ncvMjPerM3) {}

    @Test
    void parse_fuelWithNoteReference_keepsExcelNameAndRemovesNoteFromFuel() throws IOException {
        List<NationalRow> national = defaultNationalRows();
        national.set(0, new NationalRow("1A1ai Public electricity", "Power stations", "Fuel Oil [note 8]",
                77.82, 40.94, "GJ/tonne", 1.0));
        national.set(1, new NationalRow(null, "Power stations", "Colliery methane [Note 7]",
                68.61, 15.57, "MJ/m3", 1.0));
        national.set(2, new NationalRow(null, "Power stations", "Natural Gas", 56.0, 38.0, "MJ/m3", 1.0));

        ParsedEmissionFactorsData result = parser.parse(workbook(national, defaultRegionalRows()));

        NationalInventoryRow fuelOil = result.nationalInventory().get(0);
        assertThat(fuelOil.ukGhgFuel()).isEqualTo("Fuel Oil [note 8]");
        assertThat(fuelOil.fuel()).isEqualTo("Fuel Oil");

        NationalInventoryRow collieryMethane = result.nationalInventory().get(1);
        assertThat(collieryMethane.ukGhgFuel()).isEqualTo("Colliery methane [Note 7]");
        assertThat(collieryMethane.fuel()).isEqualTo("Colliery methane");

        NationalInventoryRow naturalGas = result.nationalInventory().get(2);
        assertThat(naturalGas.ukGhgFuel()).isEqualTo("Natural Gas");
        assertThat(naturalGas.fuel()).isEqualTo("Natural Gas");
    }

    @Test
    void parse_duplicateFuelInSector_appendsIndustryAndRemovesNote() throws IOException {
        List<NationalRow> national = defaultNationalRows();
        national.set(0, new NationalRow("1A2gvii Other manufacturing", "Iron and steel", "Coke [note 1]",
                104.86, 29.8, "GJ/tonne", 1.0));
        national.set(1, new NationalRow(null, "Sinter Production", "Coke [note 1]", 104.86, 29.8, "GJ/tonne", 1.0));

        ParsedEmissionFactorsData result = parser.parse(workbook(national, defaultRegionalRows()));

        assertThat(result.nationalInventory().get(0).sectorCode()).isEqualTo("1A2gviii");
        assertThat(result.nationalInventory().get(0).ukGhgFuel()).isEqualTo("Coke [note 1] (Iron and steel)");
        assertThat(result.nationalInventory().get(0).fuel()).isEqualTo("Coke (Iron and steel)");
        assertThat(result.nationalInventory().get(1).ukGhgFuel()).isEqualTo("Coke [note 1] (Sinter Production)");
        assertThat(result.nationalInventory().get(1).fuel()).isEqualTo("Coke (Sinter Production)");
    }

    @Test
    void parse_nationalInventory_mapsSectorsUnitsAndValues() throws IOException {
        List<NationalRow> national = defaultNationalRows();
        national.set(0, new NationalRow("1A1ai Public electricity", "Power stations", "Coal",
                93.89, 23.62, "GJ/tonne", 0.98));
        national.set(1, new NationalRow(null, "Power stations", "Sour gas", 74.32, 34.12, "MJ/m3", null));
        national.set(2, new NationalRow(null, "Power stations", "Biomass", null, null, null, null));
        national.set(3, new NationalRow("1B1bii Coke ovens", "Coke ovens", "Coke Oven Gas", 42.55, 16.2, "MJ/m3", 1.0));

        ParsedEmissionFactorsData result = parser.parse(workbook(national, defaultRegionalRows()));

        assertThat(result.nationalInventory()).hasSize(NATIONAL_INVENTORY_ROWS - 1);

        NationalInventoryRow coal = result.nationalInventory().get(0);
        assertThat(coal.sectorCode()).isEqualTo("1A1a");
        assertThat(coal.emissionFactor()).isEqualByComparingTo("93.89");
        assertThat(coal.netCalorificValue()).isEqualByComparingTo("23.62");
        assertThat(coal.oxidationFactor()).isEqualByComparingTo("0.98");
        assertThat(coal.ncvMeasurementUnit()).isEqualTo(NCVMeasurementUnit.GJ_PER_TONNE);

        NationalInventoryRow sourGas = result.nationalInventory().get(1);
        assertThat(sourGas.sectorCode()).isEqualTo("1A1a");
        assertThat(sourGas.netCalorificValue()).isEqualByComparingTo("0.03412");
        assertThat(sourGas.ncvMeasurementUnit()).isEqualTo(NCVMeasurementUnit.GJ_PER_NM3);
        assertThat(sourGas.oxidationFactor()).isEqualByComparingTo(BigDecimal.ONE);

        assertThat(result.nationalInventory()).extracting(NationalInventoryRow::fuel).doesNotContain("Biomass");
        assertThat(result.nationalInventory().get(2).sectorCode()).isEqualTo("1B1b");
    }

    @Test
    void parse_regionalNaturalGas_convertsNcvAndAddsNorthernIrelandAsCopyOfStranraer() throws IOException {
        List<RegionalRow> regional = defaultRegionalRows();
        regional.set(0, new RegionalRow("EA", 56.521, 39.46));
        regional.set(14, new RegionalRow("Stornoway", null, null));
        regional.set(15, new RegionalRow("Stranraer", 57.2597, 39.87));

        ParsedEmissionFactorsData result = parser.parse(workbook(defaultNationalRows(), regional));

        assertThat(result.regionalNaturalGas()).hasSize(17);

        RegionalNaturalGasRow eastern = result.regionalNaturalGas().getFirst();
        assertThat(eastern.chargingZoneCode()).isEqualTo("EA");
        assertThat(eastern.emissionFactor()).isEqualByComparingTo("56.521");
        assertThat(eastern.netCalorificValue().doubleValue()).isCloseTo(39.46 / 947.6, within(1e-12));

        RegionalNaturalGasRow stornoway = result.regionalNaturalGas().get(14);
        assertThat(stornoway.emissionFactor()).isNull();
        assertThat(stornoway.netCalorificValue()).isNull();

        RegionalNaturalGasRow stranraer = result.regionalNaturalGas().get(15);
        RegionalNaturalGasRow northernIreland = result.regionalNaturalGas().get(16);
        assertThat(northernIreland.chargingZoneCode()).isEqualTo("NI");
        assertThat(northernIreland.emissionFactor()).isEqualTo(stranraer.emissionFactor());
        assertThat(northernIreland.netCalorificValue()).isEqualTo(stranraer.netCalorificValue());
    }

    @Test
    void parse_anyNumberOfRows_skipsRowsWithoutNeededValues() throws IOException {
        List<NationalRow> national = new ArrayList<>(defaultNationalRows().subList(0, 2));
        national.add(new NationalRow(null, null, null, null, null, null, null));
        national.add(new NationalRow("Some note without a fuel", null, null, null, null, null, null));
        national.add(new NationalRow(null, "Power stations", "Fuel 3", 1.0, 1.0, "GJ/tonne", 1.0));
        List<RegionalRow> regional = new ArrayList<>(List.of(
                new RegionalRow("EA", 56.0, 39.0),
                new RegionalRow(null, 1.0, 1.0),
                new RegionalRow("Stranraer", 57.0, 39.8)));

        ParsedEmissionFactorsData result = parser.parse(workbook(national, regional));

        assertThat(result.nationalInventory()).extracting(NationalInventoryRow::fuel)
                .containsExactly("Fuel 1", "Fuel 2", "Fuel 3");
        assertThat(result.nationalInventory()).extracting(NationalInventoryRow::sectorCode)
                .containsOnly("1A1a");
        assertThat(result.regionalNaturalGas()).extracting(RegionalNaturalGasRow::chargingZoneCode)
                .containsExactly("EA", "Stranraer", "NI");
    }

    @Test
    void parse_emptyNationalInventoryTable_throwsParseError() throws IOException {
        byte[] file = workbook(List.of(), defaultRegionalRows());

        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_FILE_PARSE_ERROR);
    }

    @Test
    void parse_regionalTableWithoutStranraer_throwsParseError() throws IOException {
        byte[] file = workbook(defaultNationalRows(), List.of(new RegionalRow("EA", 56.0, 39.0)));

        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_FILE_PARSE_ERROR);
    }

    @Test
    void parse_rowsWithoutValues_areExcluded() throws IOException {
        List<NationalRow> national = defaultNationalRows();
        national.set(1, new NationalRow(null, "Power stations", "Natural Gas ", "[note 12]", "[note 12]", "[note 12]", 1.0));
        national.set(2, new NationalRow(null, "Power stations", "Biomass", "[note 4]", "[note 5]", "[note 5]", 1.0));
        national.set(3, new NationalRow(null, "Power stations", "Sour gas", null, null, null, null));
        List<RegionalRow> regional = defaultRegionalRows();
        regional.set(14, new RegionalRow("Stornoway", "[note 3]", null));

        ParsedEmissionFactorsData result = parser.parse(workbook(national, regional));

        assertThat(result.nationalInventory()).hasSize(NATIONAL_INVENTORY_ROWS - 3);
        assertThat(result.nationalInventory()).extracting(NationalInventoryRow::fuel)
                .doesNotContain("Natural Gas", "Biomass", "Sour gas");
        assertThat(result.nationalInventory()).allSatisfy(row -> {
            assertThat(row.emissionFactor()).isNotNull();
            assertThat(row.netCalorificValue()).isNotNull();
        });
        assertThat(result.regionalNaturalGas().get(14).emissionFactor()).isNull();
    }

    @Test
    void parse_rowWithoutValuesSharingAFuelName_doesNotAddIndustryToTheOtherRow() throws IOException {
        List<NationalRow> national = defaultNationalRows();
        national.set(1, new NationalRow(null, "Power stations", "Natural Gas", "[note 12]", "[note 12]", "[note 12]", 1.0));
        national.set(2, new NationalRow(null, "Gas turbines", "Natural Gas", 56.0, 38.0, "MJ/m3", 1.0));

        ParsedEmissionFactorsData result = parser.parse(workbook(national, defaultRegionalRows()));

        assertThat(result.nationalInventory()).extracting(NationalInventoryRow::fuel)
                .contains("Natural Gas")
                .doesNotContain("Natural Gas (Gas turbines)");
    }

    @Test
    void parse_nationalInventoryWithoutAnyValues_throwsParseError() throws IOException {
        List<NationalRow> national = List.of(
                new NationalRow("1A1ai Public electricity", "Power stations", "Natural Gas", "[note 12]", "[note 12]", "[note 12]", 1.0));
        byte[] file = workbook(national, defaultRegionalRows());

        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_FILE_PARSE_ERROR);
    }

    @Test
    void parse_unexpectedTextInNationalValueColumn_throwsParseError() throws IOException {
        List<NationalRow> national = defaultNationalRows();
        national.set(1, new NationalRow(null, "Power stations", "Coal", "n/a", 23.62, "GJ/tonne", 1.0));
        byte[] file = workbook(national, defaultRegionalRows());

        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_FILE_PARSE_ERROR);
    }

    @Test
    void parse_unexpectedTextInRegionalValueColumn_throwsParseError() throws IOException {
        List<RegionalRow> regional = defaultRegionalRows();
        regional.set(0, new RegionalRow("EA", 56.0, "see note below"));
        byte[] file = workbook(defaultNationalRows(), regional);

        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_FILE_PARSE_ERROR);
    }

    @Test
    void parse_numericZoneCode_throwsParseError() throws IOException {
        List<RegionalRow> regional = defaultRegionalRows();
        regional.set(0, new RegionalRow(123, 56.0, 39.0));
        byte[] file = workbook(defaultNationalRows(), regional);

        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_FILE_PARSE_ERROR);
    }

    @Test
    void parse_ncvWithoutUnit_throwsParseError() throws IOException {
        List<NationalRow> national = defaultNationalRows();
        national.set(1, new NationalRow(null, "Power stations", "Coal", 93.89, 23.62, null, 1.0));
        byte[] file = workbook(national, defaultRegionalRows());

        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_FILE_PARSE_ERROR);
    }

    @Test
    void parse_missingSheet_throwsParseError() throws IOException {
        byte[] file;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.createSheet(NATIONAL_INVENTORY_SHEET);
            workbook.write(out);
            file = out.toByteArray();
        }

        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_FILE_PARSE_ERROR);
    }

    @Test
    void parse_notAnExcelFile_throwsParseError() {
        byte[] file = "not an excel file".getBytes();

        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MetsErrorCode.EMISSION_FACTORS_FILE_PARSE_ERROR);
    }

    private List<NationalRow> defaultNationalRows() {
        List<NationalRow> rows = new ArrayList<>();
        rows.add(new NationalRow("1A1ai Public electricity", "Power stations", "Fuel 1", 1.0, 1.0, "GJ/tonne", 1.0));
        for (int i = 2; i <= NATIONAL_INVENTORY_ROWS; i++) {
            rows.add(new NationalRow(null, "Power stations", "Fuel " + i, 1.0, 1.0, "GJ/tonne", 1.0));
        }
        return rows;
    }

    private List<RegionalRow> defaultRegionalRows() {
        return new ArrayList<>(REGIONAL_ZONE_CODES.stream()
                .map(code -> new RegionalRow(code, 56.0, 39.0))
                .toList());
    }

    private byte[] workbook(List<NationalRow> nationalRows, List<RegionalRow> regionalRows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet national = workbook.createSheet(NATIONAL_INVENTORY_SHEET);
            for (int i = 0; i < nationalRows.size(); i++) {
                NationalRow spec = nationalRows.get(i);
                Row row = national.createRow(DATA_START_ROW_INDEX + i);
                setString(row, 0, spec.crtCategory());
                setString(row, 1, spec.industry());
                setString(row, 2, spec.fuel());
                setValue(row, 6, spec.emissionFactor());
                setValue(row, 10, spec.ncv());
                setString(row, 11, spec.ncvUnit());
                setValue(row, 12, spec.oxidationFactor());
            }

            Sheet regional = workbook.createSheet(REGIONAL_NATURAL_GAS_SHEET);
            for (int i = 0; i < regionalRows.size(); i++) {
                RegionalRow spec = regionalRows.get(i);
                Row row = regional.createRow(DATA_START_ROW_INDEX + i);
                setValue(row, 1, spec.zoneCode());
                setValue(row, 3, spec.emissionFactor());
                setValue(row, 6, spec.ncvMjPerM3());
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void setString(Row row, int column, String value) {
        if (value != null) {
            row.createCell(column).setCellValue(value);
        }
    }

    private void setValue(Row row, int column, Object value) {
        if (value instanceof Number number) {
            row.createCell(column).setCellValue(number.doubleValue());
        } else if (value != null) {
            row.createCell(column).setCellValue(value.toString());
        }
    }
}
