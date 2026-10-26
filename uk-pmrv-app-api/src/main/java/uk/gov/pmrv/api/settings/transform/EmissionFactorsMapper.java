package uk.gov.pmrv.api.settings.transform;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uk.gov.netz.api.common.config.MapperConfig;
import uk.gov.pmrv.api.settings.domain.EmissionFactorsHistoryEntry;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsHistoryEntryDTO;

@Mapper(componentModel = "spring", config = MapperConfig.class)
public interface EmissionFactorsMapper {

    @Mapping(target = "uploadedAt", expression = "java(entry.getUploadedAt().toLocalDate())")
    EmissionFactorsHistoryEntryDTO toEmissionFactorsHistoryEntryDTO(EmissionFactorsHistoryEntry entry);
}
