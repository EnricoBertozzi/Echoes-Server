package com.n0hana.echoes_server.scenario;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.n0hana.echoes_server.auscultation.AuscultationPointModel;
import com.n0hana.echoes_server.auscultation.AuscultationPointService;
import com.n0hana.echoes_server.infra.file.FileStorageService;

@Service
public class ScenarioService {

  @Autowired
  private ScenarioRepository scenarioRepository;

  @Autowired
  private FileStorageService fileService;

  @Autowired
  private AuscultationPointService auscultationPointService;

  public Path newScenario(ScenarioModel model, MultipartFile file) {
    String newName = this.renameFile(model, file);
    Path audioPath = fileService.saveFile(file, newName);
    model.setAudioUrl(audioPath.toString());

    AuscultationPointModel point = auscultationPointService.findPointById(model.getAuscultationPoint().getId());

    model.setAuscultationPoint(point);
    scenarioRepository.save(model);
    return audioPath;
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

}
