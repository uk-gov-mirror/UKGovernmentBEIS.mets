package uk.gov.pmrv.api.settings.domain.dto;

import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmissionFactorsHistoryEntryDTO {

    private Integer referenceYear;
    private UUID fileUuid;
    private String fileName;
    private LocalDate uploadedAt;
}
