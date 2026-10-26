package uk.gov.pmrv.api.settings.repository;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.gov.netz.api.common.AbstractContainerBaseTest;
import uk.gov.pmrv.api.reporting.domain.EmissionFactorMeasurementUnit;
import uk.gov.pmrv.api.reporting.domain.NCVMeasurementUnit;
import uk.gov.pmrv.api.settings.domain.EmissionFactorsHistoryEntry;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsDetailDTO;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsNationalInventoryEntryDTO;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsRegionalNaturalGasEntryDTO;
import uk.gov.pmrv.api.settings.domain.enumeration.EmissionFactorsHistoryEntryStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
@DataJpaTest
class EmissionFactorsRepositoryIT extends AbstractContainerBaseTest {

    @Autowired
    private EmissionFactorsRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findLatestPerReferenceYear_multipleUploadsForSameYear_returnsOnlyLatest() {
        persist(2025);
        UUID latestUuid = persist(2025);

        flushAndClear();

        List<EmissionFactorsHistoryEntry> result = repository.findLatestPerReferenceYear();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getReferenceYear()).isEqualTo(2025);
        assertThat(result.getFirst().getFileUuid()).isEqualTo(latestUuid);
    }

    @Test
    void findLatestPerReferenceYear_multipleYears_returnsOnePerYearSortedDescending() {
        persist(2023);
        persist(2024);
        persist(2025);

        flushAndClear();

        List<EmissionFactorsHistoryEntry> result = repository.findLatestPerReferenceYear();

        assertThat(result).hasSize(3);
        assertThat(result).extracting(EmissionFactorsHistoryEntry::getReferenceYear)
                .containsExactly(2025, 2024, 2023);
    }

    @Test
    void findLatestPerReferenceYear_noEntries_returnsEmpty() {
        List<EmissionFactorsHistoryEntry> result = repository.findLatestPerReferenceYear();

        assertThat(result).isEmpty();
    }

    @Test
    void findLatestByReferenceYear_multipleUploadsForSameYear_returnsLatest() {
        persist(2025);
        UUID latestUuid = persist(2025);

        flushAndClear();

        Optional<EmissionFactorsHistoryEntry> result = repository.findLatestByReferenceYear(2025);

        assertThat(result).isPresent();
        assertThat(result.get().getFileUuid()).isEqualTo(latestUuid);
    }

    @Test
    void findLatestByReferenceYear_yearNotFound_returnsEmpty() {
        persist(2024);

        flushAndClear();

        Optional<EmissionFactorsHistoryEntry> result = repository.findLatestByReferenceYear(2025);

        assertThat(result).isEmpty();
    }

    @Test
    void save_withData_roundTripsJsonSnapshot() {
        EmissionFactorsDetailDTO data = EmissionFactorsDetailDTO.builder()
                .nationalInventory(List.of(EmissionFactorsNationalInventoryEntryDTO.builder()
                        .sectorName("1A1a")
                        .ukGhgFuel("Fuel Oil [note 8]")
                        .fuel("Fuel Oil")
                        .emissionFactor(new BigDecimal("77.82237102670165"))
                        .netCalorificValue(new BigDecimal("40.94668189962701"))
                        .oxidationFactor(BigDecimal.ONE)
                        .ncvMeasurementUnit(NCVMeasurementUnit.GJ_PER_TONNE)
                        .efMeasurementUnit(EmissionFactorMeasurementUnit.TONNES_OF_CO2_PER_TJ)
                        .build()))
                .regionalNaturalGas(List.of(EmissionFactorsRegionalNaturalGasEntryDTO.builder()
                        .chargingZoneCode("Stornoway")
                        .chargingZoneName("Stornoway")
                        .calculationFactor(new BigDecimal("0.9476"))
                        .build()))
                .build();
        entityManager.persist(EmissionFactorsHistoryEntry.builder()
                .referenceYear(2026)
                .fileUuid(UUID.randomUUID())
                .fileName("uk-ggi-carbon-emission-factors-and-calorific-values-2026.xlsx")
                .uploadedAt(LocalDateTime.now())
                .uploadedBy("Jane Smith")
                .userId("user1")
                .data(data)
                .status(EmissionFactorsHistoryEntryStatus.COMPLETED)
                .build());

        flushAndClear();

        assertThat(repository.findLatestByReferenceYear(2026))
                .get()
                .extracting(EmissionFactorsHistoryEntry::getData)
                .isEqualTo(data);
    }

    @Test
    void findLatestPerReferenceYear_ignoresPendingUploads() {
        UUID completedUuid = persist(2025);
        persist(2025, EmissionFactorsHistoryEntryStatus.PENDING);
        persist(2026, EmissionFactorsHistoryEntryStatus.PENDING);

        flushAndClear();

        assertThat(repository.findLatestPerReferenceYear())
                .extracting(EmissionFactorsHistoryEntry::getFileUuid)
                .containsExactly(completedUuid);
    }

    @Test
    void findLatestByReferenceYear_ignoresPendingUploads() {
        UUID completedUuid = persist(2025);
        persist(2025, EmissionFactorsHistoryEntryStatus.PENDING);
        persist(2026, EmissionFactorsHistoryEntryStatus.PENDING);

        flushAndClear();

        assertThat(repository.findLatestByReferenceYear(2025)).get()
                .extracting(EmissionFactorsHistoryEntry::getFileUuid).isEqualTo(completedUuid);
        assertThat(repository.findLatestByReferenceYear(2026)).isEmpty();
    }

    @Test
    void findByReferenceYearAndStatus_returnsOnlyThePendingUploadsOfTheYear() {
        persist(2026);
        UUID pendingUuid = persist(2026, EmissionFactorsHistoryEntryStatus.PENDING);
        persist(2025, EmissionFactorsHistoryEntryStatus.PENDING);

        flushAndClear();

        assertThat(repository.findByReferenceYearAndStatus(2026, EmissionFactorsHistoryEntryStatus.PENDING))
                .extracting(EmissionFactorsHistoryEntry::getFileUuid)
                .containsExactly(pendingUuid);
    }

    @Test
    void findByStatus_returnsCompletedUpdatesNewestFirstAndPaged() {
        UUID oldest = persistSubmitted(2025, LocalDateTime.of(2025, 3, 1, 9, 0));
        UUID newest = persistSubmitted(2026, LocalDateTime.of(2026, 6, 21, 12, 20));
        UUID middle = persistSubmitted(2026, LocalDateTime.of(2026, 2, 10, 15, 45));
        persist(2026, EmissionFactorsHistoryEntryStatus.PENDING);

        flushAndClear();

        Sort newestFirst = Sort.by(Sort.Direction.DESC, "uploadedAt", "id");
        Page<EmissionFactorsHistoryEntry> firstPage =
                repository.findByStatus(EmissionFactorsHistoryEntryStatus.COMPLETED, PageRequest.of(0, 2, newestFirst));
        Page<EmissionFactorsHistoryEntry> secondPage =
                repository.findByStatus(EmissionFactorsHistoryEntryStatus.COMPLETED, PageRequest.of(1, 2, newestFirst));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent()).extracting(EmissionFactorsHistoryEntry::getFileUuid).containsExactly(newest, middle);
        assertThat(secondPage.getContent()).extracting(EmissionFactorsHistoryEntry::getFileUuid).containsExactly(oldest);
    }

    private UUID persistSubmitted(int referenceYear, LocalDateTime completedAt) {
        UUID fileUuid = UUID.randomUUID();
        entityManager.persist(EmissionFactorsHistoryEntry.builder()
                .referenceYear(referenceYear)
                .fileUuid(fileUuid)
                .fileName("uk-ggi-carbon-emission-factors-and-calorific-values-" + referenceYear + ".xlsx")
                .uploadedAt(completedAt)
                .uploadedBy("Jane Smith")
                .userId("user1")
                .status(EmissionFactorsHistoryEntryStatus.COMPLETED)
                .build());
        entityManager.flush();
        return fileUuid;
    }

    private UUID persist(int referenceYear) {
        return persist(referenceYear, EmissionFactorsHistoryEntryStatus.COMPLETED);
    }

    private UUID persist(int referenceYear, EmissionFactorsHistoryEntryStatus status) {
        UUID fileUuid = UUID.randomUUID();
        entityManager.persist(EmissionFactorsHistoryEntry.builder()
                .referenceYear(referenceYear)
                .fileUuid(fileUuid)
                .fileName("uk-ggi-carbon-emission-factors-and-calorific-values-" + referenceYear + ".xlsx")
                .uploadedAt(LocalDateTime.now())
                .uploadedBy("Jane Smith")
                .userId("user1")
                .status(status)
                .build());
        entityManager.flush();
        return fileUuid;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
