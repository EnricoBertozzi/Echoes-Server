
package com.n0hana.echoes_server.device;

import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.UUID;

import com.n0hana.echoes_server.scenario.ScenarioModel;

import jakarta.validation.constraints.NotNull;

public class DeviceScenarioDTO {

    /** Define uma escolha de audio para um ponto do animal. */
    public record PresetPoint(
        @NotNull UUID pointId,
        @NotNull UUID scenarioId
    ) {}

    public record CreateClinicalPresetRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) String description,
        @NotEmpty List<@Valid PresetPoint> points
    ) {}

    public record ClinicalPresetInfo(
        UUID id,
        String name,
        String description,
        List<PresetPoint> points
    ) {}

    public record SelectScenarioRequest(
        @NotNull(message = "O cenário é obrigatório")
        UUID scenarioId
    ) {}

    public record ScenarioRef(
        UUID id,
        String name,
        String description,
        String audioUri,
        String audioRevision
    ) {

        // Compatibilidade com chamadas anteriores.
        public ScenarioRef(
            UUID id,
            String name,
            String description,
            String audioUri
        ) {
            this(
                id,
                name,
                description,
                audioUri,
                null
            );
        }

        public static ScenarioRef from(
            ScenarioModel scenario
        ) {
            String path = scenario.getAudioUrl();

            String revisionSource =
                path != null && !path.isBlank()
                    ? path
                    : scenario.getId().toString();

            String revision = UUID.nameUUIDFromBytes(
                revisionSource.getBytes(
                    StandardCharsets.UTF_8
                )
            ).toString();

            return new ScenarioRef(
                scenario.getId(),
                scenario.getName(),
                scenario.getDescription(),
                "/api/v1/scenarios/"
                    + scenario.getId()
                    + "/audio",
                revision
            );
        }
    }

    public record PointConfig(
        UUID pointId,
        String position,
        Integer sensorIndex,
        ScenarioRef scenario
    ) {

        // Compatibilidade com o construtor antigo.
        public PointConfig(
            UUID pointId,
            String position,
            ScenarioRef scenario
        ) {
            this(
                pointId,
                position,
                null,
                scenario
            );
        }
    }

    public record DeviceConfig(
        UUID deviceId,
        List<PointConfig> points
    ) {}
}
