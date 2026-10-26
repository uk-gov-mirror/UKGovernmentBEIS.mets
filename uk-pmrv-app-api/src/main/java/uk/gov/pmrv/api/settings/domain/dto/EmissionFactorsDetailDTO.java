package uk.gov.pmrv.api.settings.domain.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmissionFactorsDetailDTO {

    private List<EmissionFactorsNationalInventoryEntryDTO> nationalInventory;

    private List<EmissionFactorsRegionalNaturalGasEntryDTO> regionalNaturalGas;
}
