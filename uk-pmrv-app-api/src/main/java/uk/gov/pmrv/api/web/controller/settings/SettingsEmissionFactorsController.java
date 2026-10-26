package uk.gov.pmrv.api.web.controller.settings;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.mapstruct.factory.Mappers;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.netz.api.authorization.core.domain.AppUser;
import uk.gov.netz.api.files.common.domain.dto.FileDTO;
import uk.gov.netz.api.security.Authorized;
import uk.gov.netz.api.token.FileToken;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsDetailDTO;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsHistoryEntryDTO;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsHistoryResponseDTO;
import uk.gov.pmrv.api.settings.domain.dto.EmissionFactorsUploadDTO;
import uk.gov.pmrv.api.settings.service.SettingsEmissionFactorsService;
import uk.gov.pmrv.api.web.controller.exception.ErrorResponse;
import uk.gov.pmrv.api.web.util.FileDtoMapper;

import static uk.gov.pmrv.api.web.constants.SwaggerApiInfo.FORBIDDEN;
import static uk.gov.pmrv.api.web.constants.SwaggerApiInfo.INTERNAL_SERVER_ERROR;
import static uk.gov.pmrv.api.web.constants.SwaggerApiInfo.NOT_FOUND;
import static uk.gov.pmrv.api.web.constants.SwaggerApiInfo.OK;

@RestController
@RequestMapping(path = "/v1.0/installation/settings/emission-factors")
@RequiredArgsConstructor
@Tag(name = "Settings")
@ConditionalOnProperty(prefix = "ui.features", name = "settings", havingValue = "true")
@Validated
public class SettingsEmissionFactorsController {

    private final SettingsEmissionFactorsService settingsEmissionFactorsService;
    private final FileDtoMapper fileDtoMapper = Mappers.getMapper(FileDtoMapper.class);

    @GetMapping
    @Operation(summary = "Retrieves the list of emission factors entries sorted by reference year descending")
    @ApiResponse(responseCode = "200", description = OK, content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, array = @ArraySchema(schema = @Schema(implementation = EmissionFactorsHistoryEntryDTO.class))))
    @ApiResponse(responseCode = "403", description = FORBIDDEN, content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))})
    @ApiResponse(responseCode = "500", description = INTERNAL_SERVER_ERROR, content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))})
    @Authorized
    public ResponseEntity<List<EmissionFactorsHistoryEntryDTO>> getEmissionFactors(
            @Parameter(hidden = true) AppUser appUser) {

        return ResponseEntity.ok(settingsEmissionFactorsService.getEmissionFactors());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Validates and parses an emission factors xlsx file for the given reference year and keeps it as a pending upload until it is submitted")
    @ApiResponse(responseCode = "200", description = OK, content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = EmissionFactorsUploadDTO.class)))
    @ApiResponse(responseCode = "400", description = "Bad request", content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))})
    @ApiResponse(responseCode = "403", description = FORBIDDEN, content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))})
    @ApiResponse(responseCode = "500", description = INTERNAL_SERVER_ERROR, content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))})
    @Authorized
    public ResponseEntity<EmissionFactorsUploadDTO> uploadEmissionFactors(
            @Parameter(hidden = true) AppUser appUser,
            @RequestParam("referenceYear") @Parameter(description = "The reference year") @Min(2000) @Max(2100) Integer referenceYear,
            @RequestPart(value = "file", required = false) @Parameter(description = "The emission factors xlsx file", required = true) MultipartFile file) throws IOException {

        FileDTO fileDTO = fileDtoMapper.toFileDTO(file);
        return ResponseEntity.ok(settingsEmissionFactorsService.uploadEmissionFactors(referenceYear, fileDTO, appUser));
    }

    @GetMapping("/history")
    @Operation(summary = "Retrieves the paginated history of submitted emission factors updates, newest first")
    @ApiResponse(responseCode = "200", description = OK, content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = EmissionFactorsHistoryResponseDTO.class)))
    @ApiResponse(responseCode = "403", description = FORBIDDEN, content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))})
    @ApiResponse(responseCode = "500", description = INTERNAL_SERVER_ERROR, content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))})
    @Authorized
    public ResponseEntity<EmissionFactorsHistoryResponseDTO> getEmissionFactorsHistory(
            @Parameter(hidden = true) AppUser appUser,
            @RequestParam(value = "page") @NotNull @Min(value = 0, message = "{parameter.page.typeMismatch}") @Parameter(description = "The page number starting from zero") Integer page,
            @RequestParam(value = "size") @NotNull @Min(value = 1, message = "{parameter.pageSize.typeMismatch}") @Parameter(description = "The page size") Integer pageSize) {

        return ResponseEntity.ok(settingsEmissionFactorsService.getEmissionFactorsHistory(page, pageSize));
    }

    @GetMapping("/{referenceYear}")
    @Operation(summary = "Retrieves the emission factors detail data for the given reference year")
    @ApiResponse(responseCode = "200", description = OK, content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = EmissionFactorsDetailDTO.class)))
    @ApiResponse(responseCode = "403", description = FORBIDDEN, content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))})
    @ApiResponse(responseCode = "404", description = NOT_FOUND, content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))})
    @ApiResponse(responseCode = "500", description = INTERNAL_SERVER_ERROR, content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))})
    @Authorized
    public ResponseEntity<EmissionFactorsDetailDTO> getEmissionFactorsDetail(
            @Parameter(hidden = true) AppUser appUser,
            @PathVariable @Parameter(description = "The reference year") @Min(2000) @Max(2100) Integer referenceYear) {

        return ResponseEntity.ok(settingsEmissionFactorsService.getEmissionFactorsDetail(referenceYear));
    }

    @PostMapping("/uploads/{fileUuid}/submit")
    @Operation(summary = "Submits the data of a pending upload, making it the emission factors of its reference year")
    @ApiResponse(responseCode = "204", description = "No content")
    @ApiResponse(responseCode = "400", description = "Bad request", content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))})
    @ApiResponse(responseCode = "403", description = FORBIDDEN, content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))})
    @ApiResponse(responseCode = "500", description = INTERNAL_SERVER_ERROR, content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))})
    @Authorized
    public ResponseEntity<Void> submitEmissionFactors(
            @Parameter(hidden = true) AppUser appUser,
            @PathVariable @Parameter(description = "The file uuid returned by the upload") UUID fileUuid) {

        settingsEmissionFactorsService.submitEmissionFactors(fileUuid, appUser);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/{referenceYear}/file")
    @Operation(summary = "Generates a token to download the emission factors file for the given reference year")
    @ApiResponse(responseCode = "200", description = OK, content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FileToken.class)))
    @ApiResponse(responseCode = "403", description = FORBIDDEN, content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))})
    @ApiResponse(responseCode = "404", description = NOT_FOUND, content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))})
    @ApiResponse(responseCode = "500", description = INTERNAL_SERVER_ERROR, content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))})
    @Authorized
    public ResponseEntity<FileToken> generateGetEmissionFactorsFileToken(
            @Parameter(hidden = true) AppUser appUser,
            @PathVariable @Parameter(description = "The reference year") @Min(2000) @Max(2100) Integer referenceYear) {

        return ResponseEntity.ok(settingsEmissionFactorsService.generateGetEmissionFactorsFileToken(referenceYear));
    }
}
