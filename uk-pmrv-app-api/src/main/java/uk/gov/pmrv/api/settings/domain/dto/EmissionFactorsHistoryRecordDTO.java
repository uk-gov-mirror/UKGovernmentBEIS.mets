package uk.gov.pmrv.api.settings.domain.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class EmissionFactorsHistoryRecordDTO {

    private LocalDateTime changedAt;
    private String changedBy;
    private Integer referenceYear;
}
