package uk.gov.pmrv.api.settings.domain.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.pmrv.api.reporting.domain.EmissionFactorMeasurementUnit;
import uk.gov.pmrv.api.reporting.domain.NCVMeasurementUnit;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmissionFactorsNationalInventoryEntryDTO {

    private Long id;
    private String sectorName;
    private String ukGhgFuel;
    private String fuel;
    private BigDecimal emissionFactor;
    private BigDecimal netCalorificValue;
    private BigDecimal oxidationFactor;
    private NCVMeasurementUnit ncvMeasurementUnit;
    private EmissionFactorMeasurementUnit efMeasurementUnit;
}
