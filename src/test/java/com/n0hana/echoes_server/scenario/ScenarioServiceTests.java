package com.n0hana.echoes_server.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.n0hana.echoes_server.infra.file.FileStorageService;

@SpringBootTest
@ActiveProfiles("test")
class ScenarioServiceTests {

    @Autowired private ScenarioService service;
    @Autowired private ScenarioRepository repository;
    @Autowired private FileStorageService files;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private String oldFile;
    private UUID scenarioId;

    @BeforeEach
    void setUp() throws Exception {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        tx = new TransactionTemplate(transactionManager);

        oldFile = UUID.randomUUID() + "_original.mp3";
        Path file = files.load(oldFile);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] { 1, 2, 3 });

        scenarioId = repository.save(ScenarioModel.builder()
                .name("original").description("d").audioUrl(oldFile).build()).getId();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (scenarioId != null) {
            repository.findById(scenarioId).ifPresent(s -> {
                files.delete(s.getAudioUrl());
                repository.delete(s);
            });
        }
        Files.deleteIfExists(files.load(oldFile));
        RequestContextHolder.resetRequestAttributes();
    }

    private ScenarioModel renamed(String name) {
        return ScenarioModel.builder().name(name).description("d").build();
    }

    @Test
    @DisplayName("Sucesso: o arquivo só é renomeado depois do commit e o banco aponta para o novo nome")
    void renamesFileAfterCommit() {
        Path old = files.load(oldFile);

        tx.executeWithoutResult(status -> {
            service.updateScenario(scenarioId, renamed("novo nome"));
            assertTrue(Files.exists(old), "antes do commit o arquivo antigo continua no lugar");
        });

        ScenarioModel saved = repository.findById(scenarioId).orElseThrow();
        assertEquals("novo nome", saved.getName());
        assertNotEquals(oldFile, saved.getAudioUrl());
        assertTrue(saved.getAudioUrl().contains("novo_nome"));
        assertFalse(Files.exists(old), "arquivo antigo não existe mais");
        assertTrue(Files.exists(files.load(saved.getAudioUrl())), "arquivo novo existe");
    }

    @Test
    @DisplayName("Rollback: o arquivo continua com o nome antigo e o cenário não muda")
    void rollbackKeepsOldFileAndRecord() {
        tx.executeWithoutResult(status -> {
            service.updateScenario(scenarioId, renamed("nome descartado"));
            status.setRollbackOnly();
        });

        ScenarioModel saved = repository.findById(scenarioId).orElseThrow();
        assertEquals("original", saved.getName());
        assertEquals(oldFile, saved.getAudioUrl());
        assertTrue(Files.exists(files.load(oldFile)), "arquivo antigo preservado");
    }
}
