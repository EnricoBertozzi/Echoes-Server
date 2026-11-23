package com.n0hana.echoes_server.infra.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.n0hana.echoes_server.infra.file.exception.FileValidateException;
import com.n0hana.echoes_server.infra.file.exception.StorageFileNotFoundException;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTests {

    private static final String SUB_DIR = "turma-1";
    private static final List<String> EXTENSIONS = List.of("pdf");

    @TempDir
    Path tempDir;

    @Mock
    private FileValidatorService validator;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setUploadDir(tempDir.toString());
        service = new FileStorageService(properties, validator);
    }

    @Test
    @DisplayName("Guarda o arquivo na subpasta e cria a pasta")
    void storesFileInSubDir() {
        MockMultipartFile file = pdf();

        String name = service.store(file, "Aula 1", SUB_DIR, EXTENSIONS);

        assertTrue(name.endsWith("_Aula_1.pdf"));
        assertTrue(Files.exists(tempDir.resolve(SUB_DIR).resolve(name)));
        verify(validator).validate(file, EXTENSIONS);
    }

    @Test
    @DisplayName("Não guarda o arquivo quando a validação falha")
    void doesNotStoreInvalidFile() {
        MockMultipartFile file = pdf();
        doThrow(new FileValidateException()).when(validator).validate(any(MultipartFile.class), any());

        assertThrows(FileValidateException.class, () -> service.store(file, "Aula 1", SUB_DIR, EXTENSIONS));

        assertFalse(Files.exists(tempDir.resolve(SUB_DIR)));
    }

    @Test
    @DisplayName("Localiza o arquivo dentro da subpasta")
    void loadsFileFromSubDir() {
        Path path = service.load(SUB_DIR, "arquivo.pdf");

        assertEquals(tempDir.toAbsolutePath().normalize().resolve(SUB_DIR).resolve("arquivo.pdf"), path);
    }

    @Test
    @DisplayName("Recusa caminho que sai da pasta de uploads")
    void rejectsPathOutsideUploadDir() {
        assertThrows(StorageFileNotFoundException.class, () -> service.load(SUB_DIR, "../../fora.pdf"));
    }

    @Test
    @DisplayName("Exclui o arquivo da subpasta")
    void deletesFileFromSubDir() {
        String name = service.store(pdf(), "Aula 1", SUB_DIR, EXTENSIONS);
        Path stored = tempDir.resolve(SUB_DIR).resolve(name);
        assertTrue(Files.exists(stored));

        service.delete(SUB_DIR, name);

        assertFalse(Files.exists(stored));
    }

    @Test
    @DisplayName("Excluir arquivo que não existe não gera erro")
    void deleteMissingFileDoesNothing() {
        service.delete(SUB_DIR, "nao-existe.pdf");
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "aula.pdf", "application/pdf", "conteudo".getBytes());
    }
}
