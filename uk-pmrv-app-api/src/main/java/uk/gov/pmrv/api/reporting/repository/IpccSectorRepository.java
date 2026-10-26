package uk.gov.pmrv.api.reporting.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.pmrv.api.reporting.domain.IpccSector;

@Repository
public interface IpccSectorRepository extends JpaRepository<IpccSector, Long> {
}
