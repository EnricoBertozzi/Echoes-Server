package com.n0hana.echoes_server.scenario;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;

@RestController
@RequestMapping("/scenarios")
public class ScenarioController {

  @Autowired
  private ScenarioService scenarioService;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(encoding = @Encoding(name = "dto", contentType = MediaType.APPLICATION_JSON_VALUE)))
  public ResponseEntity<Void> newScenario(@RequestPart("dto") ScenarioDTO.ScenarioRegister dto,
      @RequestPart("file") MultipartFile file) {
    scenarioService.newScenario(dto.toModel(), file);
    return ResponseEntity.ok().build();
  }

  @GetMapping
  public ResponseEntity<List<ScenarioDTO.ScenarioInfo>> findAllScenariosByPoint(@RequestParam int page, @RequestParam int size,
      @RequestParam UUID id) {
    List<ScenarioDTO.ScenarioInfo> list = scenarioService.findScenariosByPoint(id, page, size)
        .stream()
        .map(ScenarioDTO.ScenarioInfo::from)
        .toList();
    return ResponseEntity.ok(list);
  }

  @GetMapping("/search")
  public ResponseEntity<List<ScenarioDTO.ScenarioInfo>> findAllScenariosByName(@RequestParam int page, @RequestParam int size,
      @RequestParam String name) {
    List<ScenarioDTO.ScenarioInfo> list = scenarioService.findScenariosByName(page, size, name)
        .stream()
        .map(ScenarioDTO.ScenarioInfo::from)
        .toList();
    return ResponseEntity.ok(list);
  }

  @GetMapping("/{id}")
  public ResponseEntity<ScenarioDTO.ScenarioInfo> findScenarioById(@PathVariable("id") UUID id) {
    
    ScenarioDTO.ScenarioInfo dto = ScenarioDTO.ScenarioInfo.from(scenarioService.findScenarioById(id));
    return ResponseEntity.ok(dto);
  }

  @PatchMapping("/{id}")
  public ResponseEntity<Void> updateScenario(@PathVariable("id") UUID id, @RequestBody ScenarioDTO.ScenarioUpdate dto) {
    scenarioService.updateScenario(id, dto.toModel());
    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteScenario(@PathVariable("id") UUID id) {

    return ResponseEntity.ok().build();
  }
}
