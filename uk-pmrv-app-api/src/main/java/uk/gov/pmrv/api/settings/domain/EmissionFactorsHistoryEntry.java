package uk.gov.pmrv.api.settings.domain;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsDetailDTO;
import uk.gov.pmrv.api.settings.domain.enumeration.EmissionFactorsHistoryEntryStatus;

@Entity
@Table(name = "settings_emission_factors_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmissionFactorsHistoryEntry {

    @Id
    @SequenceGenerator(name = "settings_emission_factors_history_id_generator", sequenceName = "settings_emission_factors_history_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "settings_emission_factors_history_id_generator")
    private Long id;

    @Column(name = "reference_year", nullable = false)
    private Integer referenceYear;

    @Column(name = "file_uuid", nullable = false)
    private UUID fileUuid;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "uploaded_by", nullable = false, length = 512)
    private String uploadedBy;

    @Type(JsonType.class)
    @Column(name = "data", columnDefinition = "jsonb")
    private EmissionFactorsDetailDTO data;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EmissionFactorsHistoryEntryStatus status;
}
