package com.n0hana.echoes_server.infra.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.n0hana.echoes_server.infra.file.exception.FileValidateException;
import com.n0hana.echoes_server.infra.file.exception.StorageDeleteFileException;
import com.n0hana.echoes_server.infra.file.exception.StorageFileNotFoundException;
import com.n0hana.echoes_server.infra.file.exception.StorageSaveFileException;

class FileStorageServiceCoverageTests {

    @TempDir Path dir;

    private FileValidatorService validator;
    private FileStorageService service;

    @BeforeEach
    void setUp() {
        validator = mock(FileValidatorService.class);
        FileStorageProperties props = new FileStorageProperties();
        props.setUploadDir(dir.resolve("uploads").toString());
        service = new FileStorageService(props, validator);
        service.init();
    }

    private Path uploads() { return dir.resolve("uploads"); }

    private MockMultipartFile file(String name) {
        return new MockMultipartFile("file", name, "application/octet-stream", new byte[] { 1, 2, 3 });
    }

    @Test
    @DisplayName("init cria a pasta de uploads e é idempotente")
    void initCreatesDirectory() {
        assertTrue(Files.isDirectory(uploads()));
        service.init();
        assertTrue(Files.isDirectory(uploads()));
    }

    @Test
    @DisplayName("init não propaga erro quando não consegue criar a pasta")
    void initSwallowsFailure() throws IOException {
        Path blocker = Files.writeString(dir.resolve("arquivo"), "x");
        FileStorageProperties props = new FileStorageProperties();
        props.setUploadDir(blocker.resolve("sub").toString());

        new FileStorageService(props, validator).init();

        assertFalse(Files.exists(blocker.resolve("sub")));
    }

    @Test
    @DisplayName("store valida e grava com nome UUID_nome_sem_espacos.ext")
    void storeSavesFile() {
        MockMultipartFile f = file("original.txt");

        String name = service.store(f, "  Meu arquivo ");

        verify(validator).validate(f);
        assertTrue(name.matches("[0-9a-f-]{36}_Meu_arquivo\\.txt"), name);
        assertTrue(Files.exists(uploads().resolve(name)));
    }

    @Test
    @DisplayName("storeAudio usa a validação de áudio")
    void storeAudioValidatesAudio() {
        MockMultipartFile f = file("som.mp3");

        String name = service.storeAudio(f, "som");

        verify(validator).validateAudio(f);
        assertTrue(name.endsWith("_som.mp3"));
        assertTrue(Files.exists(uploads().resolve(name)));
    }

    @Test
    @DisplayName("Validação reprovada impede a gravação")
    void invalidFileIsNotSaved() throws IOException {
        MockMultipartFile f = file("a.exe");
        doThrow(new FileValidateException()).when(validator).validate(f);

        assertThrows(FileValidateException.class, () -> service.store(f, "x"));
        try (var s = Files.list(uploads())) { assertEquals(0, s.count()); }
    }

    @Test
    @DisplayName("Falha ao transferir vira StorageSaveFileException")
    void transferFailure() throws IOException {
        MultipartFile f = mock(MultipartFile.class);
        org.mockito.Mockito.when(f.getOriginalFilename()).thenReturn("a.txt");
        doThrow(new IOException("x")).when(f).transferTo(org.mockito.ArgumentMatchers.any(Path.class));
        org.mockito.Mockito.when(f.getInputStream()).thenThrow(new IOException("x"));

        assertThrows(StorageSaveFileException.class, () -> service.store(f, "x"));
        assertThrows(StorageSaveFileException.class, () -> service.storeAudio(f, "x"));
        assertThrows(StorageSaveFileException.class,
                () -> service.store(f, "x", "sub", java.util.List.of("txt")));
    }

    @Test
    @DisplayName("load resolve dentro da pasta; load com subpasta recusa escapar")
    void loadResolvesAndGuards() {
        assertEquals(uploads().toAbsolutePath().normalize().resolve("a.txt"), service.load("a.txt"));
        assertEquals(uploads().toAbsolutePath().normalize().resolve("t1").resolve("a.txt"),
                service.load("t1", "a.txt"));
        assertThrows(StorageFileNotFoundException.class, () -> service.load("t1", "../../fora.txt"));
        assertThrows(StorageFileNotFoundException.class, () -> service.load("..", "fora.txt"));
    }

    @Test
    @DisplayName("delete remove o arquivo e ignora inexistente")
    void deleteRemovesFile() {
        String name = service.store(file("a.txt"), "a");
        assertTrue(Files.exists(uploads().resolve(name)));

        service.delete(name);
        assertFalse(Files.exists(uploads().resolve(name)));
        service.delete(name); // já removido: sem erro
    }

    @Test
    @DisplayName("delete falha com StorageDeleteFileException quando não consegue remover")
    void deleteFailure() throws IOException {
        Path folder = Files.createDirectories(uploads().resolve("pasta"));
        Files.writeString(folder.resolve("filho"), "x");

        assertThrows(StorageDeleteFileException.class, () -> service.delete("pasta"));
        assertThrows(StorageDeleteFileException.class, () -> service.delete(".", "pasta"));
    }

    @Test
    @DisplayName("delete em subpasta remove o arquivo e recusa sair da pasta")
    void deleteInSubDir() throws IOException {
        Files.createDirectories(uploads().resolve("t1"));
        Path target = Files.writeString(uploads().resolve("t1").resolve("a.txt"), "x");

        service.delete("t1", "a.txt");

        assertFalse(Files.exists(target));
        assertThrows(StorageFileNotFoundException.class, () -> service.delete("t1", "../../x"));
    }

    @Test
    @DisplayName("rename move o arquivo para o novo nome")
    void renameMovesFile() {
        String old = service.store(file("a.txt"), "a");

        String renamed = service.rename(old, "novo nome");

        assertTrue(renamed.matches("[0-9a-f-]{36}_novo_nome_.*|[0-9a-f-]{36}_novo_nome.*"), renamed);
        assertTrue(Files.exists(uploads().resolve(renamed)));
        assertFalse(Files.exists(uploads().resolve(old)));
    }

    @Test
    @DisplayName("targetNameFor e move recusam arquivo inexistente")
    void missingFile() {
        assertThrows(StorageFileNotFoundException.class, () -> service.targetNameFor("nao.txt", "x"));
        assertThrows(StorageFileNotFoundException.class, () -> service.move("nao.txt", "y.txt"));
        assertThrows(StorageFileNotFoundException.class, () -> service.rename("nao.txt", "x"));
    }

    @Test
    @DisplayName("move falha com StorageSaveFileException quando o destino já existe")
    void moveFailsWhenTargetExists() throws IOException {
        Files.writeString(uploads().resolve("a.txt"), "1");
        Files.writeString(uploads().resolve("b.txt"), "2");

        assertThrows(StorageSaveFileException.class, () -> service.move("a.txt", "b.txt"));
    }

    @Test
    @DisplayName("targetNameFor não move nada")
    void targetNameDoesNotMove() throws IOException {
        Files.writeString(uploads().resolve("a.txt"), "1");

        String target = service.targetNameFor("a.txt", "outro");

        assertTrue(target.endsWith("_outro.txt"));
        assertTrue(Files.exists(uploads().resolve("a.txt")));
        assertFalse(Files.exists(uploads().resolve(target)));
        assertEquals(File.separator, File.separator);
    }
}
