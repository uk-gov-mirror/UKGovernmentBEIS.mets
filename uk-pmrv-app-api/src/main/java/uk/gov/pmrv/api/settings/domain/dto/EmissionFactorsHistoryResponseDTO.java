package uk.gov.pmrv.api.settings.domain.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmissionFactorsHistoryResponseDTO {

    private List<EmissionFactorsHistoryRecordDTO> history;
    private long totalItems;
}
