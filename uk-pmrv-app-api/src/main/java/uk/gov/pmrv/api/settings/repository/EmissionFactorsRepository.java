package uk.gov.pmrv.api.settings.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.gov.pmrv.api.settings.domain.EmissionFactorsHistoryEntry;
import uk.gov.pmrv.api.settings.domain.enumeration.EmissionFactorsHistoryEntryStatus;

public interface EmissionFactorsRepository extends JpaRepository<EmissionFactorsHistoryEntry, Long> {

    @Query("SELECT e FROM EmissionFactorsHistoryEntry e " +
           "WHERE e.id IN (SELECT MAX(e2.id) FROM EmissionFactorsHistoryEntry e2 " +
           "WHERE e2.status = uk.gov.pmrv.api.settings.domain.enumeration.EmissionFactorsHistoryEntryStatus.COMPLETED GROUP BY e2.referenceYear) " +
           "ORDER BY e.referenceYear DESC")
    List<EmissionFactorsHistoryEntry> findLatestPerReferenceYear();

    @Query("SELECT e FROM EmissionFactorsHistoryEntry e " +
           "WHERE e.referenceYear = :referenceYear " +
           "AND e.id = (SELECT MAX(e2.id) FROM EmissionFactorsHistoryEntry e2 " +
           "WHERE e2.referenceYear = :referenceYear AND e2.status = uk.gov.pmrv.api.settings.domain.enumeration.EmissionFactorsHistoryEntryStatus.COMPLETED)")
    Optional<EmissionFactorsHistoryEntry> findLatestByReferenceYear(@Param("referenceYear") Integer referenceYear);

    Optional<EmissionFactorsHistoryEntry> findByFileUuid(UUID fileUuid);

    Page<EmissionFactorsHistoryEntry> findByStatus(EmissionFactorsHistoryEntryStatus status, Pageable pageable);

    List<EmissionFactorsHistoryEntry> findByReferenceYearAndStatus(Integer referenceYear, EmissionFactorsHistoryEntryStatus status);
}
