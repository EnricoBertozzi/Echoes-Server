package com.n0hana.echoes_server.scenario;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.n0hana.echoes_server.auscultation.AuscultationPointModel;
import com.n0hana.echoes_server.auscultation.AuscultationPointService;
import com.n0hana.echoes_server.infra.file.FileStorageService;
import com.n0hana.echoes_server.scenario.exception.ScenarioNotFoundException;

import jakarta.transaction.Transactional;

@Service
public class ScenarioService {

  @Autowired
  private ScenarioRepository scenarioRepository;

  @Autowired
  private FileStorageService fileService;

  @Autowired
  private AuscultationPointService auscultationPointService;

  @Transactional
  public void newScenario(ScenarioModel model, MultipartFile file) {
    String newName = this.renameFile(model, file);
    String audioPath = fileService.saveFile(file, newName);
    model.setAudioUrl(audioPath);

    AuscultationPointModel point = auscultationPointService.findPointById(model.getAuscultationPoint().getId());

    model.setAuscultationPoint(point);
    scenarioRepository.save(model);
  }

  public List<ScenarioModel> findScenariosByPoint(UUID id, int page, int size) {
    return scenarioRepository
        .findAllScenariosByAuscultationPointId(
            id,
            PageRequest.of(page, size, Sort.by("name")))
        .toList();
  }

  public List<ScenarioModel> findScenariosByName(@RequestParam int page, @RequestParam int size,
      @RequestParam String name) {
    return scenarioRepository
        .findAllScenariosByNameContaining(
            name,
            PageRequest.of(page, size, Sort.by("name")))
        .toList();
  }

  public ScenarioModel findScenarioById(UUID id) {
    return scenarioRepository.findById(id)
        .orElseThrow(() -> new ScenarioNotFoundException());
  }

  @Transactional
  public void updateScenario(UUID id, ScenarioModel scenario) {
    ScenarioModel savedScenario = this.findScenarioById(id);

    if (!savedScenario.getName().equals(scenario.getName()))
      savedScenario.setName(scenario.getName());

    if (!savedScenario.getDescription().equals(scenario.getDescription()))
      savedScenario.setDescription(scenario.getDescription());

    scenarioRepository.save(savedScenario);
  }

  @Transactional
  public void deleteScenario(UUID id) {
    this.scenarioExists(id);

    scenarioRepository.deleteById(id);
  }

  // Métodos auxiliares
  private String renameFile(ScenarioModel model, MultipartFile file) {
    String scenarioName = model.getName().replaceAll(" ", "_");
    String originalName = file.getOriginalFilename();
    String extension = "";

    if (originalName != null && originalName.contains("."))
      extension = originalName.substring(originalName.lastIndexOf("."));

    String newName = scenarioName + extension;
    return newName;
  }

  public void scenarioExists(UUID id) {
    scenarioRepository.findById(id)
        .orElseThrow(() -> new ScenarioNotFoundException());
  }
}
