package com.n0hana.echoes_server.scenario;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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

import com.n0hana.echoes_server.scenario.ScenarioDTO.ScenarioInfo;
import com.n0hana.echoes_server.scenario.ScenarioDTO.ScenarioRegister;
import com.n0hana.echoes_server.scenario.ScenarioDTO.ScenarioUpdate;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;

/**
 * Controlador REST responsável pelo gerenciamento de cenários.
 * 
 * @author Enrico Bertozzi
 * @since 0.1.0
 * @see {@link ScenarioService}
 */
@RestController
@RequestMapping("/api/v1/scenarios")
public class ScenarioController {

  @Autowired
  private ScenarioService scenarioService;

  /**
   * Cria um novo cenário clínico no sistema
   * 
   * @param dto  Objeto contendo os dados de cadastro
   * @param file Arquivo enviado para o cenário
   * @return {@link ResponseEntity} com o cenário criado e código HTTP 201
   *         (CREATED)
   */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(encoding = @Encoding(name = "dto", contentType = MediaType.APPLICATION_JSON_VALUE)))
  public ResponseEntity<ScenarioInfo> newScenario(@RequestPart("dto") ScenarioRegister dto,
      @RequestPart("file") MultipartFile file) {
    ScenarioInfo responseDto = ScenarioInfo
        .from(scenarioService.create(dto.toModel(), file));

    return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
  }

  /**
   * Busca todos os cenários cadastrados de um ponto de ausculta
   * 
   * @param page Número da página selecionada da paginação.
   * @param size Quantidade de elementos por página
   * @param id   Identificador do ponto de ausculta
   * @return {@link ResponseEntity} com a lista de cenários e o código HTTP 200
   *         (OK)
   */
  @GetMapping
  public ResponseEntity<List<ScenarioInfo>> findAllScenariosByPoint(@RequestParam int page,
      @RequestParam int size,
      @RequestParam UUID id) {
    List<ScenarioInfo> list = scenarioService.findScenariosByPoint(id, page, size)
        .stream()
        .map(ScenarioInfo::from)
        .toList();
    return ResponseEntity.ok(list);
  }

  /**
   * Filtra cenários que contenham o nome fornecido
   * 
   * @param page Número da página selecionada da paginação.
   * @param size Quantidade de elementos por página
   * @param name Nome utilizado para o filtro
   * @return {@link ResponseEntity} com a lista de cenários que atendam ao
   *         predicado e o códigp HTTP 200 (OK)
   */
  @GetMapping("/search")
  public ResponseEntity<List<ScenarioInfo>> findAllScenariosByName(@RequestParam int page,
      @RequestParam int size,
      @RequestParam String name) {
    List<ScenarioInfo> list = scenarioService.findScenariosByName(page, size, name)
        .stream()
        .map(ScenarioInfo::from)
        .toList();
    return ResponseEntity.ok(list);
  }

  /**
   * Busca um cenário pelo seu id
   * 
   * @param id Identificador do cenário
   * @return {@link ResponseEntity} com o cenário encontrado e o código HTTP 200
   *         (OK)
   */
  @GetMapping("/{id}")
  public ResponseEntity<ScenarioInfo> findScenarioById(@PathVariable("id") UUID id) {

    ScenarioInfo dto = ScenarioInfo.from(scenarioService.findScenarioById(id));
    return ResponseEntity.ok(dto);
  }

  /**
   * Atualiza os dados de um cenário
   * 
   * @param id  Identificador do cenário
   * @param dto Objeto contendo os dados para a alteração
   * @return {@link ResponseEntity} com o cenário atualizado e código HTTP 200 (OK)
   */
  @PatchMapping("/{id}")
  public ResponseEntity<ScenarioInfo> updateScenario(@PathVariable("id") UUID id, @RequestBody ScenarioUpdate dto) {
    ScenarioInfo responseDTO = ScenarioInfo.from(
        scenarioService.updateScenario(id, dto.toModel()));
    return ResponseEntity.ok(responseDTO);
  }

  /**
   * Atualiza o arquivo de áudio cadastrado a um cenário
   * 
   * @param file Novo arquivo de áudio para o cenário
   * @param id Identificador do cenário
   * @return {@link ResponseEntity} com código HTTP 204 (NO CONTENT)
   */
  @PatchMapping(value = "/{id}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> reuploadFile(@RequestPart("file") MultipartFile file, @PathVariable("id") UUID id) {
    scenarioService.reuploadFile(id, file);
    return ResponseEntity.noContent().build();
  }

  /**
   * Deleta um cenário cadastrado no sistema.
   * 
   * @param id Identificador do cenário
   * @return {@link ResponseEntity} com código HTTP 204  (NO CONTENT)
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteScenario(@PathVariable("id") UUID id) {
    scenarioService.deleteScenario(id);
    return ResponseEntity.noContent().build();
  }
}
