package com.n0hana.echoes_server.scenario;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.n0hana.echoes_server.auscultation.AuscultationPointModel;
import com.n0hana.echoes_server.auscultation.AuscultationPointService;
import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.infra.file.FileStorageService;
import com.n0hana.echoes_server.infra.file.exception.StorageFileNotFoundException;
import com.n0hana.echoes_server.scenario.exception.ScenarioNotFoundException;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

/**
 * Service para gerenciamento de cenários clínicos
 *
 * @author Enrico Bertozzi
 * @since 0.1.0
 * @see {@link ScenarioModel}
 */
@Service
@RequiredArgsConstructor
public class ScenarioService {

  private static final long MAX_AUDIO_BYTES = 512L * 1024L;

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
  public ScenarioModel create(ScenarioModel model, MultipartFile file) {
    validateDeviceAudio(file);

    AuscultationPointModel point = auscultationPointService.findPointById(model.getAuscultationPoint().getId());
    model.setAuscultationPoint(point);

    String audioPath = fileService.store(file, model.getName());
    model.setAudioUrl(audioPath);

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
    if (device == null || device.getAnimal() == null) {
      return List.of();
    }

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
  public List<ScenarioModel> findScenariosByName(int page, int size, String name) {
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
    if (device == null || device.getAnimal() == null) {
      return List.of();
    }

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
        .orElseThrow(ScenarioNotFoundException::new);
  }

  /**
   * Busca o cenário garantindo que ele pertence ao animal do dispositivo
   * autenticado, quando houver um.
   */
  public ScenarioModel findScenarioById(UUID id, DeviceModel device) {
    ScenarioModel scenario = findScenarioById(id);

    if (device != null) {
      if (scenario.getAuscultationPoint() == null) {
        throw new AccessDeniedException("Cenário sem ponto de ausculta válido");
      }

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
    if (device == null
        || device.getInstitution() == null
        || !device.getInstitution().isActive()
        || device.getClassroom() == null
        || device.getClassroom().isArchived()
        || device.getClassroom().isDeleted()) {
      throw new AccessDeniedException("Dispositivo não autorizado a baixar áudio");
    }

    ScenarioModel scenario = findScenarioById(id, device);
    Path path = fileService.load(scenario.getAudioUrl());

    if (!Files.exists(path)) {
      throw new StorageFileNotFoundException();
    }

    return path;
  }

  /**
   * Atualiza os dados de um cenário
   *
   * @param id       Identificador do cenário.
   * @param scenario Objeto com dados a serem atualizados
   */
  @Transactional
  public ScenarioModel updateScenario(UUID id, ScenarioModel scenario) {
    ScenarioModel savedScenario = findScenarioById(id);

    // Mantém o caminho físico original para não quebrar a referência do áudio salvo
    savedScenario.setName(scenario.getName());
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
    ScenarioModel model = findScenarioById(id);

    scenarioRepository.deleteById(id);
    deleteAfterCommit(model.getAudioUrl());
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
        .orElseThrow(ScenarioNotFoundException::new);
  }

  /**
   * Reupload de novo arquivo para um cenário existente
   *
   * @param id   Identificador do cenário
   * @param file Arquivo de áudio enviado.
   */
  @Transactional
  public void reuploadFile(UUID id, MultipartFile file) {
    validateDeviceAudio(file);

    ScenarioModel model = findScenarioById(id);
    String previousPath = model.getAudioUrl();

    String newPath = fileService.store(file, model.getName());
    model.setAudioUrl(newPath);
    scenarioRepository.save(model);

    // Apaga o MP3 antigo só após o commit; o novo path gera outra audioRevision no DTO
    if (previousPath != null && !previousPath.equals(newPath)) {
      deleteAfterCommit(previousPath);
    }
  }

  private void deleteAfterCommit(String path) {
    if (path == null || path.isBlank()) {
      return;
    }

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              fileService.delete(path);
            }
          });
    } else {
      fileService.delete(path);
    }
  }

  private static void validateDeviceAudio(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getSize() > MAX_AUDIO_BYTES) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "O arquivo MP3 deve ter no máximo 512 KiB");
    }

    String originalName = file.getOriginalFilename();

    if (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(".mp3")) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Somente arquivos MP3 são permitidos");
    }
  }
}
