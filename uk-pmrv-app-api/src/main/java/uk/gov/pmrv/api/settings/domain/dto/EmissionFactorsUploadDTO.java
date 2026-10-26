package uk.gov.pmrv.api.settings.domain.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmissionFactorsUploadDTO {

    /** Identifies the pending upload, sent back to confirm exactly this previewed data. */
    private UUID fileUuid;

    private EmissionFactorsDetailDTO data;
}
