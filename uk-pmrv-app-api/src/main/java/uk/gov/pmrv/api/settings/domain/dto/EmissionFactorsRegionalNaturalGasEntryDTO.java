package uk.gov.pmrv.api.settings.domain.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmissionFactorsRegionalNaturalGasEntryDTO {

    private String chargingZoneCode;
    private String chargingZoneName;
    private BigDecimal emissionFactor;
    private BigDecimal netCalorificValue;
    private BigDecimal calculationFactor;
}
