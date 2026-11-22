package com.n0hana.echoes_server.scenario;

import com.n0hana.echoes_server.infra.web.PageLimits;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

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
import com.n0hana.echoes_server.infra.file.exception.StorageFileNotFoundException;
import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.scenario.exception.ScenarioNotFoundException;

import jakarta.transaction.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service para gerenciamento dde cenários clínicos
 * 
 * @author Enrico Bertozzi
 * @since 0.1.0
 * @see {@link ScenarioModel}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScenarioService {

  private final ScenarioRepository scenarioRepository;

  private final FileStorageService fileService;

  private final AuscultationPointService auscultationPointService;

  /**
   * Salva um novo cenário no sistema
   * 
   * @param model Objeto com dados para armazenamento
   * @param file  Arquivo de áudio enviado.
   */
  @Transactional
  @Auditable(action = "CREATE", entity = "Scenario", entityId = "#result.id", entityName = "#result.name")
  public ScenarioModel create(ScenarioModel model, MultipartFile file) {
    String audioPath = fileService.storeAudio(file, model.getName());

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
            PageRequest.of(PageLimits.clampPage(page), PageLimits.clamp(size), Sort.by("name")))
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
            PageRequest.of(PageLimits.clampPage(page), PageLimits.clamp(size), Sort.by("name")))
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
            PageRequest.of(PageLimits.clampPage(page), PageLimits.clamp(size), Sort.by("name")))
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
            PageRequest.of(PageLimits.clampPage(page), PageLimits.clamp(size), Sort.by("name")))
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
   * Localiza o arquivo de áudio do cenário para o dispositivo baixar, garantindo
   * que o cenário pertence ao animal do dispositivo autenticado.
   *
   * @param id     Identificador do cenário
   * @param device Dispositivo autenticado
   * @return {@link Path} do arquivo de áudio
   */
  public Path findAudioPath(UUID id, DeviceModel device) {
    ScenarioModel scenario = findScenarioById(id, device);
    Path path = fileService.load(scenario.getAudioUrl());
    if (!Files.exists(path))
      throw new StorageFileNotFoundException();
    return path;
  }

  /**
   * Atualiza os dados de um cenário
   * 
   * @param id       Identificador do cenário.
   * @param scenario Objeto com dados a serem atualizados
   */
  @Transactional
  @Auditable(action = "UPDATE", entity = "Scenario", entityId = "#result.id", entityName = "#result.name")
  public ScenarioModel updateScenario(UUID id, ScenarioModel scenario) {
    ScenarioModel savedScenario = this.findScenarioById(id);

    if (!Objects.equals(savedScenario.getName(), scenario.getName())) {
      String oldAudioUrl = savedScenario.getAudioUrl();
      String newAudioUrl = fileService.targetNameFor(oldAudioUrl, scenario.getName());
      savedScenario.setAudioUrl(newAudioUrl);
      savedScenario.setName(scenario.getName());
      moveFileAfterCommit(oldAudioUrl, newAudioUrl);
    }

    if (!Objects.equals(savedScenario.getDescription(), scenario.getDescription()))
      savedScenario.setDescription(scenario.getDescription());

    return scenarioRepository.save(savedScenario);
  }

  /**
   * Exclui um cenário do sistema
   * 
   * @param id Identificador do cenário a ser excluído.
   */
  @Transactional
  @Auditable(action = "DELETE", entity = "Scenario", entityId = "#id")
  public void deleteScenario(UUID id) {
    ScenarioModel model = this.findScenarioById(id);
    String audioUrl = model.getAudioUrl();

    scenarioRepository.delete(model);
    deleteFileAfterCommit(audioUrl);
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
  @Transactional
  @Auditable(action = "REPLACE_FILE", entity = "Scenario", entityId = "#id")
  public void reuploadFile(UUID id, MultipartFile file) {
    ScenarioModel model = this.findScenarioById(id);
    String oldAudioUrl = model.getAudioUrl();

    String newAudioUrl = fileService.storeAudio(file, model.getName());
    model.setAudioUrl(newAudioUrl);
    scenarioRepository.save(model);

    deleteFileAfterCommit(oldAudioUrl);
    deleteFileIfRollback(newAudioUrl);
  }

  /** renomeia o arquivo só depois que a transação comitou; em rollback ele fica como estava */
  private void moveFileAfterCommit(String oldAudioUrl, String newAudioUrl) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          try {
            fileService.move(oldAudioUrl, newAudioUrl);
          } catch (RuntimeException ex) {
            log.error("Falha ao renomear o áudio de {} para {} após o commit", oldAudioUrl, newAudioUrl, ex);
          }
        }
      });
    } else {
      fileService.move(oldAudioUrl, newAudioUrl);
    }
  }

  /** remove o arquivo só depois que a transação comitou (ou já, sem transação) */
  private void deleteFileAfterCommit(String audioUrl) {
    if (audioUrl == null || audioUrl.isBlank())
      return;

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          fileService.delete(audioUrl);
        }
      });
    } else {
      fileService.delete(audioUrl);
    }
  }

  /** arquivo recém-gravado some se a transação for desfeita */
  private void deleteFileIfRollback(String audioUrl) {
    if (!TransactionSynchronizationManager.isSynchronizationActive())
      return;

    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        if (status == STATUS_ROLLED_BACK)
          fileService.delete(audioUrl);
      }
    });
  }
}
