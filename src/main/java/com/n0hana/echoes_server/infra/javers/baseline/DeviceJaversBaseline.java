package com.n0hana.echoes_server.infra.javers.baseline;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.n0hana.echoes_server.device.ClinicalScenarioPresetModel;
import com.n0hana.echoes_server.device.ClinicalScenarioPresetRepository;
import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.device.DeviceRepository;
import com.n0hana.echoes_server.device.DeviceScenarioModel;
import com.n0hana.echoes_server.device.DeviceScenarioRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DeviceJaversBaseline {

    private final DeviceRepository devices;
    private final DeviceScenarioRepository deviceScenarios;
    private final ClinicalScenarioPresetRepository presets;
    private final JaversBaselineSupport support;

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        support.baseline("dispositivos", (Iterable<DeviceModel>) () -> devices.findAll().iterator(), DeviceModel::getId);
        support.baseline("cenários de dispositivo", (Iterable<DeviceScenarioModel>) () -> deviceScenarios.findAll().iterator(), DeviceScenarioModel::getId);
        support.baseline("presets clínicos", (Iterable<ClinicalScenarioPresetModel>) () -> presets.findAll().iterator(), ClinicalScenarioPresetModel::getId);
    }
}
