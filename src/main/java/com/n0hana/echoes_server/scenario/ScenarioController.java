package com.n0hana.echoes_server.scenario;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.parameters.RequestBody;

@RestController
@RequestMapping("/scenarios")
public class ScenarioController {

  @Autowired
  private ScenarioService scenarioService;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RequestBody(content = @Content(encoding = @Encoding(name = "dto", contentType = MediaType.APPLICATION_JSON_VALUE)))
  public ResponseEntity<Void> newScenario(@RequestPart("dto") ScenarioDTO.Register dto, @RequestPart("file") MultipartFile file) {
    scenarioService.newScenario(dto.toModel(), file);
    return ResponseEntity.ok().build();
  }
}
