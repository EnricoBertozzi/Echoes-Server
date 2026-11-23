package com.n0hana.echoes_server.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.n0hana.echoes_server.animal.AnimalModel;
import com.n0hana.echoes_server.auscultation.AuscultationPointModel;
import com.n0hana.echoes_server.auscultation.AuscultationPointService;
import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.device.DeviceScenarioRepository;
import com.n0hana.echoes_server.infra.file.FileStorageService;
import com.n0hana.echoes_server.infra.file.exception.StorageFileNotFoundException;
import com.n0hana.echoes_server.scenario.exception.ScenarioInUseException;
import com.n0hana.echoes_server.scenario.exception.ScenarioNotFoundException;

@ExtendWith(MockitoExtension.class)
class ScenarioServiceCoverageTests {

    @Mock
    private ScenarioRepository scenarioRepository;
    @Mock
    private FileStorageService fileService;
    @Mock
    private AuscultationPointService auscultationPointService;
    @Mock
    private DeviceScenarioRepository deviceScenarioRepository;

    @InjectMocks
    private ScenarioService service;

    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.clearSynchronization();
    }

    private ScenarioModel scenario(String name, String desc, String audio) {
        ScenarioModel s = ScenarioModel.builder().id(UUID.randomUUID()).name(name).description(desc).audioUrl(audio)
                .build();
        return s;
    }

    private DeviceModel deviceWithAnimal(UUID animalId) {
        AnimalModel animal = AnimalModel.builder().id(animalId).build();
        DeviceModel device = new DeviceModel();
        device.setAnimal(animal);
        return device;
    }

    private Page<ScenarioModel> page(ScenarioModel... items) {
        return new PageImpl<>(List.of(items));
    }

    private List<TransactionSynchronization> syncs() {
        return TransactionSynchronizationManager.getSynchronizations();
    }

    // ---------- create ----------

    @Test
    @DisplayName("Cria cenário armazenando o áudio e associando o ponto persistido")
    void shouldCreateScenario() {
        UUID pointId = UUID.randomUUID();
        ScenarioModel model = ScenarioModel.builder().name("Sopro")
                .auscultationPoint(AuscultationPointModel.builder().id(pointId).build()).build();
        AuscultationPointModel point = AuscultationPointModel.builder().id(pointId).position("Mitral").build();
        MockMultipartFile file = new MockMultipartFile("file", "a.mp3", "audio/mpeg", new byte[] { 1 });

        when(fileService.storeAudio(file, "Sopro")).thenReturn("sopro.mp3");
        when(auscultationPointService.findPointById(pointId)).thenReturn(point);
        when(scenarioRepository.save(model)).thenReturn(model);

        ScenarioModel result = service.create(model, file);

        assertSame(model, result);
        assertEquals("sopro.mp3", result.getAudioUrl());
        assertSame(point, result.getAuscultationPoint());
    }

    // ---------- listagens ----------

    @Test
    @DisplayName("Lista cenários do ponto aplicando clamp de página e tamanho, ordenando por nome")
    void shouldFindScenariosByPointClamped() {
        UUID id = UUID.randomUUID();
        ScenarioModel s = scenario("A", "d", null);
        when(scenarioRepository.findAllScenariosByAuscultationPointId(eq(id), any(Pageable.class)))
                .thenReturn(page(s));

        List<ScenarioModel> result = service.findScenariosByPoint(id, -5, 100000);

        assertEquals(List.of(s), result);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(scenarioRepository).findAllScenariosByAuscultationPointId(eq(id), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(100, captor.getValue().getPageSize());
        assertTrue(captor.getValue().getSort().getOrderFor("name") != null);
    }

    @Test
    @DisplayName("Lista cenários do ponto validando posse pelo dispositivo")
    void shouldFindScenariosByPointForDevice() {
        UUID id = UUID.randomUUID();
        DeviceModel device = deviceWithAnimal(UUID.randomUUID());
        ScenarioModel s = scenario("A", "d", null);
        when(scenarioRepository.findAllScenariosByAuscultationPointId(eq(id), any(Pageable.class)))
                .thenReturn(page(s));

        List<ScenarioModel> result = service.findScenariosByPoint(id, 0, 10, device);

        assertEquals(List.of(s), result);
        verify(auscultationPointService).findPointById(id, device);
    }

    @Test
    @DisplayName("Não lista cenários do ponto quando o dispositivo não é dono dele")
    void shouldNotListScenariosWhenPointNotOwned() {
        UUID id = UUID.randomUUID();
        DeviceModel device = deviceWithAnimal(UUID.randomUUID());
        when(auscultationPointService.findPointById(id, device)).thenThrow(new AccessDeniedException("x"));

        assertThrows(AccessDeniedException.class, () -> service.findScenariosByPoint(id, 0, 10, device));
        verifyNoInteractions(scenarioRepository);
    }

    @Test
    @DisplayName("Dispositivo nulo ou sem animal não enxerga cenários")
    void shouldReturnEmptyForDeviceWithoutAnimal() {
        assertTrue(service.findScenariosByDevice(0, 10, null).isEmpty());
        assertTrue(service.findScenariosByDevice(0, 10, new DeviceModel()).isEmpty());
        assertTrue(service.findScenariosByNameForDevice(0, 10, "x", null).isEmpty());
        assertTrue(service.findScenariosByNameForDevice(0, 10, "x", new DeviceModel()).isEmpty());
        verifyNoInteractions(scenarioRepository);
    }

    @Test
    @DisplayName("Lista cenários do animal do dispositivo")
    void shouldFindScenariosByDevice() {
        UUID animalId = UUID.randomUUID();
        ScenarioModel s = scenario("A", "d", null);
        when(scenarioRepository.findAllScenariosByAuscultationPointAnimalId(eq(animalId), any(Pageable.class)))
                .thenReturn(page(s));

        assertEquals(List.of(s), service.findScenariosByDevice(1, 5, deviceWithAnimal(animalId)));
    }

    @Test
    @DisplayName("Filtra cenários por nome")
    void shouldFindScenariosByName() {
        ScenarioModel s = scenario("Sopro", "d", null);
        when(scenarioRepository.findAllScenariosByNameContaining(eq("So"), any(PageRequest.class)))
                .thenReturn(page(s));

        assertEquals(List.of(s), service.findScenariosByName(0, 10, "So"));
    }

    @Test
    @DisplayName("Filtra cenários por nome restritos ao animal do dispositivo")
    void shouldFindScenariosByNameForDevice() {
        UUID animalId = UUID.randomUUID();
        ScenarioModel s = scenario("Sopro", "d", null);
        when(scenarioRepository.findAllScenariosByNameContainingAndAuscultationPointAnimalId(eq("So"), eq(animalId),
                any(Pageable.class))).thenReturn(page(s));

        assertEquals(List.of(s), service.findScenariosByNameForDevice(0, 10, "So", deviceWithAnimal(animalId)));
    }

    // ---------- findScenarioById ----------

    @Test
    @DisplayName("Busca cenário por id e lança NotFound se não existir")
    void shouldFindByIdOrThrow() {
        UUID id = UUID.randomUUID();
        ScenarioModel s = scenario("A", "d", null);
        when(scenarioRepository.findById(id)).thenReturn(Optional.of(s));
        assertSame(s, service.findScenarioById(id));

        UUID missing = UUID.randomUUID();
        when(scenarioRepository.findById(missing)).thenReturn(Optional.empty());
        assertThrows(ScenarioNotFoundException.class, () -> service.findScenarioById(missing));
        assertThrows(ScenarioNotFoundException.class, () -> service.scenarioExists(missing));
        service.scenarioExists(id);
    }

    @Test
    @DisplayName("Sem dispositivo, retorna o cenário sem checar posse")
    void shouldFindByIdWithoutDevice() {
        ScenarioModel s = scenario("A", "d", null);
        when(scenarioRepository.findById(s.getId())).thenReturn(Optional.of(s));

        assertSame(s, service.findScenarioById(s.getId(), null));
        verifyNoInteractions(auscultationPointService);
    }

    @Test
    @DisplayName("Com dispositivo, valida o ponto do cenário")
    void shouldFindByIdWithDevice() {
        UUID pointId = UUID.randomUUID();
        ScenarioModel s = scenario("A", "d", null);
        s.setAuscultationPoint(AuscultationPointModel.builder().id(pointId).build());
        DeviceModel device = deviceWithAnimal(UUID.randomUUID());
        when(scenarioRepository.findById(s.getId())).thenReturn(Optional.of(s));

        assertSame(s, service.findScenarioById(s.getId(), device));
        verify(auscultationPointService).findPointById(pointId, device);
    }

    @Test
    @DisplayName("Dispositivo não acessa cenário sem ponto de ausculta")
    void shouldDenyScenarioWithoutPoint() {
        ScenarioModel s = scenario("A", "d", null);
        DeviceModel device = deviceWithAnimal(UUID.randomUUID());
        when(scenarioRepository.findById(s.getId())).thenReturn(Optional.of(s));

        assertThrows(AccessDeniedException.class, () -> service.findScenarioById(s.getId(), device));
    }

    // ---------- findAudioPath ----------

    @Test
    @DisplayName("Retorna o caminho do áudio quando o arquivo existe")
    void shouldReturnAudioPath(@TempDir Path dir) throws Exception {
        Path file = Files.createFile(dir.resolve("a.mp3"));
        ScenarioModel s = scenario("A", "d", "a.mp3");
        when(scenarioRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(fileService.load("a.mp3")).thenReturn(file);

        assertEquals(file, service.findAudioPath(s.getId(), null));
    }

    @Test
    @DisplayName("Lança StorageFileNotFound quando o arquivo de áudio não existe")
    void shouldThrowWhenAudioMissing(@TempDir Path dir) {
        ScenarioModel s = scenario("A", "d", "a.mp3");
        when(scenarioRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(fileService.load("a.mp3")).thenReturn(dir.resolve("nao-existe.mp3"));

        assertThrows(StorageFileNotFoundException.class, () -> service.findAudioPath(s.getId(), null));
    }

    // ---------- update ----------

    @Test
    @DisplayName("Atualização sem mudanças não mexe no arquivo nem nos campos")
    void shouldUpdateWithoutChanges() {
        ScenarioModel saved = scenario("Nome", "Desc", "nome.mp3");
        when(scenarioRepository.findById(saved.getId())).thenReturn(Optional.of(saved));
        when(scenarioRepository.save(saved)).thenReturn(saved);

        ScenarioModel result = service.updateScenario(saved.getId(), scenario("Nome", "Desc", null));

        assertSame(saved, result);
        assertEquals("nome.mp3", result.getAudioUrl());
        verifyNoInteractions(fileService);
    }

    @Test
    @DisplayName("Mudar só a descrição não renomeia o áudio")
    void shouldUpdateOnlyDescription() {
        ScenarioModel saved = scenario("Nome", "Desc", "nome.mp3");
        when(scenarioRepository.findById(saved.getId())).thenReturn(Optional.of(saved));
        when(scenarioRepository.save(saved)).thenReturn(saved);

        service.updateScenario(saved.getId(), scenario("Nome", "Nova descrição", null));

        assertEquals("Nova descrição", saved.getDescription());
        verifyNoInteractions(fileService);
    }

    @Test
    @DisplayName("Renomear sem transação ativa move o arquivo imediatamente")
    void shouldRenameImmediatelyWithoutTransaction() {
        ScenarioModel saved = scenario("Velho", "Desc", "velho.mp3");
        when(scenarioRepository.findById(saved.getId())).thenReturn(Optional.of(saved));
        when(scenarioRepository.save(saved)).thenReturn(saved);
        when(fileService.targetNameFor("velho.mp3", "Novo")).thenReturn("novo.mp3");

        service.updateScenario(saved.getId(), scenario("Novo", "Desc", null));

        assertEquals("Novo", saved.getName());
        assertEquals("novo.mp3", saved.getAudioUrl());
        verify(fileService).move("velho.mp3", "novo.mp3");
    }

    @Test
    @DisplayName("Renomear com transação só move o arquivo após o commit")
    void shouldRenameAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        ScenarioModel saved = scenario("Velho", "Desc", "velho.mp3");
        when(scenarioRepository.findById(saved.getId())).thenReturn(Optional.of(saved));
        when(scenarioRepository.save(saved)).thenReturn(saved);
        when(fileService.targetNameFor("velho.mp3", "Novo")).thenReturn("novo.mp3");

        service.updateScenario(saved.getId(), scenario("Novo", "Desc", null));

        verify(fileService, never()).move(anyString(), anyString());
        assertEquals(1, syncs().size());
        syncs().get(0).afterCommit();
        verify(fileService).move("velho.mp3", "novo.mp3");
    }

    @Test
    @DisplayName("Falha ao mover após o commit é engolida (apenas logada)")
    void shouldSwallowMoveFailureAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        ScenarioModel saved = scenario("Velho", "Desc", "velho.mp3");
        when(scenarioRepository.findById(saved.getId())).thenReturn(Optional.of(saved));
        when(scenarioRepository.save(saved)).thenReturn(saved);
        when(fileService.targetNameFor("velho.mp3", "Novo")).thenReturn("novo.mp3");
        doThrow(new RuntimeException("disco")).when(fileService).move("velho.mp3", "novo.mp3");

        service.updateScenario(saved.getId(), scenario("Novo", "Desc", null));

        syncs().get(0).afterCommit();
        verify(fileService).move("velho.mp3", "novo.mp3");
    }

    // ---------- delete ----------

    @Test
    @DisplayName("Exclui cenário e remove o áudio imediatamente sem transação")
    void shouldDeleteWithoutTransaction() {
        ScenarioModel s = scenario("A", "d", "a.mp3");
        when(scenarioRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(deviceScenarioRepository.existsByScenarioId(s.getId())).thenReturn(false);

        service.deleteScenario(s.getId());

        verify(scenarioRepository).delete(s);
        verify(fileService).delete("a.mp3");
    }

    @Test
    @DisplayName("Exclui cenário e remove o áudio só após o commit")
    void shouldDeleteAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        ScenarioModel s = scenario("A", "d", "a.mp3");
        when(scenarioRepository.findById(s.getId())).thenReturn(Optional.of(s));

        service.deleteScenario(s.getId());

        verify(scenarioRepository).delete(s);
        verify(fileService, never()).delete(anyString());
        syncs().get(0).afterCommit();
        verify(fileService).delete("a.mp3");
    }

    @Test
    @DisplayName("Exclusão de cenário sem áudio (nulo ou em branco) não toca no storage")
    void shouldDeleteWithoutAudio() {
        ScenarioModel nulo = scenario("A", "d", null);
        ScenarioModel branco = scenario("B", "d", "  ");
        when(scenarioRepository.findById(nulo.getId())).thenReturn(Optional.of(nulo));
        when(scenarioRepository.findById(branco.getId())).thenReturn(Optional.of(branco));

        service.deleteScenario(nulo.getId());
        service.deleteScenario(branco.getId());

        verify(scenarioRepository).delete(nulo);
        verify(scenarioRepository).delete(branco);
        verifyNoInteractions(fileService);
    }

    @Test
    @DisplayName("Não exclui cenário em uso por dispositivo")
    void shouldNotDeleteInUse() {
        ScenarioModel s = scenario("A", "d", "a.mp3");
        when(scenarioRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(deviceScenarioRepository.existsByScenarioId(s.getId())).thenReturn(true);

        assertThrows(ScenarioInUseException.class, () -> service.deleteScenario(s.getId()));
        verify(scenarioRepository, never()).delete(any());
        verifyNoInteractions(fileService);
    }

    @Test
    @DisplayName("isInUse delega ao repositório de DeviceScenario")
    void shouldDelegateIsInUse() {
        UUID id = UUID.randomUUID();
        when(deviceScenarioRepository.existsByScenarioId(id)).thenReturn(true);
        assertTrue(service.isInUse(id));
        assertFalse(service.isInUse(UUID.randomUUID()));
    }

    // ---------- ativar / desativar ----------

    @Test
    @DisplayName("Desativa cenário ativo e salva")
    void shouldDeactivate() {
        ScenarioModel s = scenario("A", "d", null);
        when(scenarioRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(scenarioRepository.save(s)).thenReturn(s);

        ScenarioModel result = service.deactivateScenario(s.getId());

        assertFalse(result.isActive());
        verify(scenarioRepository).save(s);
    }

    @Test
    @DisplayName("Desativar cenário já inativo não salva de novo")
    void shouldNotSaveWhenAlreadyInactive() {
        ScenarioModel s = scenario("A", "d", null);
        s.setActive(false);
        when(scenarioRepository.findById(s.getId())).thenReturn(Optional.of(s));

        assertFalse(service.deactivateScenario(s.getId()).isActive());
        verify(scenarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ativa cenário inativo e ignora quando já ativo")
    void shouldActivate() {
        ScenarioModel inactive = scenario("A", "d", null);
        inactive.setActive(false);
        when(scenarioRepository.findById(inactive.getId())).thenReturn(Optional.of(inactive));
        when(scenarioRepository.save(inactive)).thenReturn(inactive);
        assertTrue(service.activateScenario(inactive.getId()).isActive());

        ScenarioModel active = scenario("B", "d", null);
        when(scenarioRepository.findById(active.getId())).thenReturn(Optional.of(active));
        assertTrue(service.activateScenario(active.getId()).isActive());
        verify(scenarioRepository, never()).save(active);
    }

    // ---------- reupload ----------

    @Test
    @DisplayName("Reupload sem transação troca o áudio e apaga o antigo imediatamente")
    void shouldReuploadWithoutTransaction() {
        ScenarioModel s = scenario("A", "d", "velho.mp3");
        MockMultipartFile file = new MockMultipartFile("file", "a.mp3", "audio/mpeg", new byte[] { 1 });
        when(scenarioRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(fileService.storeAudio(file, "A")).thenReturn("novo.mp3");

        service.reuploadFile(s.getId(), file);

        assertEquals("novo.mp3", s.getAudioUrl());
        verify(scenarioRepository).save(s);
        verify(fileService).delete("velho.mp3");
        verify(fileService, never()).delete("novo.mp3");
    }

    @Test
    @DisplayName("Reupload com transação: commit apaga o antigo, rollback apaga o novo")
    void shouldReuploadWithTransactionCommit() {
        TransactionSynchronizationManager.initSynchronization();
        ScenarioModel s = scenario("A", "d", "velho.mp3");
        MockMultipartFile file = new MockMultipartFile("file", "a.mp3", "audio/mpeg", new byte[] { 1 });
        when(scenarioRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(fileService.storeAudio(file, "A")).thenReturn("novo.mp3");

        service.reuploadFile(s.getId(), file);

        assertEquals(2, syncs().size());
        verify(fileService, never()).delete(anyString());

        // commit: dispara afterCommit (apaga o antigo) e afterCompletion COMMITTED (nada)
        for (TransactionSynchronization sync : syncs()) {
            sync.afterCommit();
            sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
        verify(fileService).delete("velho.mp3");
        verify(fileService, never()).delete("novo.mp3");
    }

    @Test
    @DisplayName("Rollback do reupload apaga o arquivo recém-gravado")
    void shouldDeleteNewFileOnRollback() {
        TransactionSynchronizationManager.initSynchronization();
        ScenarioModel s = scenario("A", "d", "velho.mp3");
        MockMultipartFile file = new MockMultipartFile("file", "a.mp3", "audio/mpeg", new byte[] { 1 });
        when(scenarioRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(fileService.storeAudio(file, "A")).thenReturn("novo.mp3");

        service.reuploadFile(s.getId(), file);

        for (TransactionSynchronization sync : syncs())
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(fileService).delete("novo.mp3");
        verify(fileService, never()).delete("velho.mp3");
    }

    @Test
    @DisplayName("Reupload de cenário sem áudio anterior só grava o novo")
    void shouldReuploadWhenNoPreviousAudio() {
        ScenarioModel s = scenario("A", "d", null);
        MockMultipartFile file = new MockMultipartFile("file", "a.mp3", "audio/mpeg", new byte[] { 1 });
        when(scenarioRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(fileService.storeAudio(file, "A")).thenReturn("novo.mp3");

        service.reuploadFile(s.getId(), file);

        assertEquals("novo.mp3", s.getAudioUrl());
        verify(fileService, never()).delete(anyString());
    }
}
