package com.n0hana.echoes_server.infra.javers.baseline;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.n0hana.echoes_server.animal.AnimalModel;
import com.n0hana.echoes_server.animal.AnimalRepository;
import com.n0hana.echoes_server.auscultation.AuscultationPointModel;
import com.n0hana.echoes_server.auscultation.AuscultationPointRepository;
import com.n0hana.echoes_server.scenario.ScenarioModel;
import com.n0hana.echoes_server.scenario.ScenarioRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ScenarioJaversBaseline {

    private final ScenarioRepository scenarios;
    private final AnimalRepository animals;
    private final AuscultationPointRepository points;
    private final JaversBaselineSupport support;

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        support.baseline("cenários", (Iterable<ScenarioModel>) () -> scenarios.findAll().iterator(), ScenarioModel::getId);
        support.baseline("animais", (Iterable<AnimalModel>) () -> animals.findAll().iterator(), AnimalModel::getId);
        support.baseline("pontos de ausculta", (Iterable<AuscultationPointModel>) () -> points.findAll().iterator(), AuscultationPointModel::getId);
    }
}
