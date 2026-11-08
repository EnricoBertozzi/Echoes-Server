package com.n0hana.echoes_server.scenario;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.n0hana.echoes_server.auscultation.AuscultationPointModel;
import com.n0hana.echoes_server.auscultation.AuscultationPointService;
import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.infra.file.FileStorageService;
import com.n0hana.echoes_server.scenario.exception.ScenarioNotFoundException;

import jakarta.transaction.Transactional;

/**
 * Service para gerenciamento dde cenários clínicos
 * 
 * @author Enrico Bertozzi
 * @since 0.1.0
 * @see {@link ScenarioModel}
 */
@Service
public class ScenarioService {

  @Autowired
  private ScenarioRepository scenarioRepository;

  @Autowired
  private FileStorageService fileService;

  @Autowired
  private AuscultationPointService auscultationPointService;

  /**
   * Salva um novo cenário no sistema
   * 
   * @param model Objeto com dados para armazenamento
   * @param file  Arquivo de áudio enviado.
   */
  @Transactional
  public ScenarioModel create(ScenarioModel model, MultipartFile file) {
    String audioPath = fileService.store(file, model.getName());

    model.setAudioUrl(audioPath);

    AuscultationPointModel point = auscultationPointService.findPointById(model.getAuscultationPoint().getId());

    model.setAuscultationPoint(point);
    return scenarioRepository.save(model);
  }

  /**
   * Busca todos os cenários de um ponto de ausculta
   * 
   * @param id   Identificador do ponto de ausculta.
   * @param page Página retorna na paginação.
   * @param size Quantidade de elementos na paginação.
   * @return {@link List} contendo todos os cenários do ponto
   */
  public List<ScenarioModel> findScenariosByPoint(UUID id, int page, int size) {
    return scenarioRepository
        .findAllScenariosByAuscultationPointId(
            id,
            PageRequest.of(page, size, Sort.by("name")))
        .toList();
  }

  /**
   * Busca os cenários de um ponto garantindo que ele pertence ao animal do
   * dispositivo autenticado, quando houver um.
   */
  public List<ScenarioModel> findScenariosByPoint(UUID id, int page, int size, DeviceModel device) {
    auscultationPointService.findPointById(id, device);
    return findScenariosByPoint(id, page, size);
  }

  /**
   * Busca todos os cenários do animal associado ao dispositivo autenticado. Um
   * dispositivo sem animal associado não enxerga nenhum cenário.
   */
  public List<ScenarioModel> findScenariosByDevice(int page, int size, DeviceModel device) {
    if (device == null || device.getAnimal() == null)
      return List.of();

    return scenarioRepository
        .findAllScenariosByAuscultationPointAnimalId(
            device.getAnimal().getId(),
            PageRequest.of(page, size, Sort.by("name")))
        .toList();
  }

  /**
   * Filtra cenários cadastrados por nome
   * 
   * @param page Página retornada pela paginação.
   * @param size Quantidade de elementos por página.
   * @param name Nome utilizado para filtro.
   * @return
   */
  public List<ScenarioModel> findScenariosByName(@RequestParam int page, @RequestParam int size,
      @RequestParam String name) {
    return scenarioRepository
        .findAllScenariosByNameContaining(
            name,
            PageRequest.of(page, size, Sort.by("name")))
        .toList();
  }

  /**
   * Filtra cenários do animal associado ao dispositivo autenticado.
   */
  public List<ScenarioModel> findScenariosByNameForDevice(int page, int size, String name, DeviceModel device) {
    if (device == null || device.getAnimal() == null)
      return List.of();

    return scenarioRepository
        .findAllScenariosByNameContainingAndAuscultationPointAnimalId(
            name,
            device.getAnimal().getId(),
            PageRequest.of(page, size, Sort.by("name")))
        .toList();
  }

  /**
   * Busca um cenário pelo id
   * 
   * @param id Identificador do cenário
   * @return {@link ScenarioModel} encontrado na busca,
   */
  public ScenarioModel findScenarioById(UUID id) {
    return scenarioRepository.findById(id)
        .orElseThrow(() -> new ScenarioNotFoundException());
  }

  /**
   * Busca o cenário garantindo que ele pertence ao animal do dispositivo
   * autenticado, quando houver um.
   */
  public ScenarioModel findScenarioById(UUID id, DeviceModel device) {
    ScenarioModel scenario = findScenarioById(id);

    if (device != null) {
      if (scenario.getAuscultationPoint() == null)
        throw new AccessDeniedException("Cenário não pertence ao dispositivo autenticado");

      auscultationPointService.findPointById(scenario.getAuscultationPoint().getId(), device);
    }

    return scenario;
  }

  /**
   * Atualiza os dados de um cenário
   * 
   * @param id       Identificador do cenário.
   * @param scenario Objeto com dados a serem atualizados
   */
  @Transactional
  public ScenarioModel updateScenario(UUID id, ScenarioModel scenario) {
    ScenarioModel savedScenario = this.findScenarioById(id);

    if (!savedScenario.getName().equals(scenario.getName())) {
      fileService.rename(savedScenario.getAudioUrl(), scenario.getName());
      savedScenario.setName(scenario.getName());
    }

    if (!savedScenario.getDescription().equals(scenario.getDescription()))
      savedScenario.setDescription(scenario.getDescription());

    return scenarioRepository.save(savedScenario);
  }

  /**
   * Exclui um cenário do sistema
   * 
   * @param id Identificador do cenário a ser excluído.
   */
  @Transactional
  public void deleteScenario(UUID id) {
    ScenarioModel model = this.findScenarioById(id);

    fileService.delete(model.getAudioUrl());
    scenarioRepository.deleteById(model.getId());
  }

  /**
   * Verifica se o cenário existe.
   * 
   * <p>
   * Busca no banco de dados por um cenário registrado com o identificador
   * fornecido. Caso não encontre, a exceção {@link ScenarioNotFoundException} é
   * lançada.
   * </p>
   * 
   * @throw ScenarioNotFoundException
   * 
   * @param id Identificador do cenário.
   */
  public void scenarioExists(UUID id) {
    scenarioRepository.findById(id)
        .orElseThrow(() -> new ScenarioNotFoundException());
  }

  /**
   * Reupload de novo arquivo para um cenário existente
   * 
   * @param id   Identificador do cenário
   * @param file Arquivo de áudio enviado.
   */
  public void reuploadFile(UUID id, MultipartFile file) {
    ScenarioModel model = this.findScenarioById(id);
    fileService.delete(model.getAudioUrl());

    String audioUrl = fileService.store(file, model.getName());
    model.setAudioUrl(audioUrl);

    scenarioRepository.save(model);
  }
}
