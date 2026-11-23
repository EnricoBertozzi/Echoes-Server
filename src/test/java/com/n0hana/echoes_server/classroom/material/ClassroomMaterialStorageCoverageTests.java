package com.n0hana.echoes_server.classroom.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.n0hana.echoes_server.infra.file.FileStorageProperties;

class ClassroomMaterialStorageCoverageTests {

    @TempDir Path dir;

    @AfterEach
    void cleanSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.clearSynchronization();
    }

    private ClassroomMaterialStorage storage(String... allowed) {
        FileStorageProperties props = new FileStorageProperties();
        props.setUploadDir(dir.toString());
        return new ClassroomMaterialStorage(props,
                allowed.length == 0 ? List.of(" PDF ", "txt") : List.of(allowed));
    }

    private Path root() { return dir.resolve("classroom-materials"); }

    private long filesInRoot() throws IOException {
        if (!Files.exists(root())) return 0;
        try (var s = Files.list(root())) { return s.count(); }
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "aula.pdf", "application/pdf", "%PDF-1.7\n".getBytes());
    }

    private String message(Runnable r) {
        return assertThrows(MaterialException.class, r::run).getMessage();
    }

    // construtor

    @Test
    @DisplayName("Sem diretório de upload configurado não inicia")
    void requiresUploadDir() {
        FileStorageProperties props = new FileStorageProperties();
        assertThrows(IllegalStateException.class, () -> new ClassroomMaterialStorage(props, List.of("pdf")));
        props.setUploadDir("  ");
        assertThrows(IllegalStateException.class, () -> new ClassroomMaterialStorage(props, List.of("pdf")));
    }

    @Test
    @DisplayName("Extensões permitidas são normalizadas (espaços e maiúsculas)")
    void normalizesAllowedExtensions() {
        var stored = storage(" PDF ").store(pdf());
        assertEquals("aula.pdf", stored.originalName());
    }

    // store

    @Test
    @DisplayName("Arquivo nulo ou vazio é recusado")
    void rejectsNullOrEmpty() {
        var storage = storage();
        assertEquals("Selecione um arquivo", message(() -> storage.store(null)));
        var empty = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[0]);
        assertEquals("Selecione um arquivo", message(() -> storage.store(empty)));
    }

    @Test
    @DisplayName("Tamanho declarado acima de 50 MB é recusado")
    void rejectsDeclaredOversize() {
        MultipartFile big = Mockito.mock(MultipartFile.class);
        Mockito.when(big.isEmpty()).thenReturn(false);
        Mockito.when(big.getSize()).thenReturn(ClassroomMaterialStorage.MAX_BYTES + 1);

        assertEquals("O limite é de 50 MB", message(() -> storage().store(big)));
    }

    @Test
    @DisplayName("Extensão conhecida mas não configurada é recusada")
    void rejectsNotConfiguredExtension() {
        var png = new MockMultipartFile("file", "x.png", "image/png", new byte[] { 1 });
        assertEquals("Tipo de arquivo não permitido para material: .png", message(() -> storage().store(png)));
    }

    @Test
    @DisplayName("Nome inválido é recusado antes de gravar")
    void rejectsInvalidFilename() throws IOException {
        var bad = new MockMultipartFile("file", "../a.pdf", "application/pdf", new byte[] { 1 });
        assertEquals("Nome de arquivo inválido", message(() -> storage().store(bad)));
        assertEquals(0, filesInRoot());
    }

    @Test
    @DisplayName("Conteúdo incompatível apaga o arquivo gravado")
    void invalidContentRemovesFile() throws IOException {
        var fake = new MockMultipartFile("file", "a.pdf", "application/pdf", "nao e pdf".getBytes());
        assertEquals("Conteúdo incompatível com a extensão do arquivo", message(() -> storage().store(fake)));
        assertEquals(0, filesInRoot());
    }

    @Test
    @DisplayName("Fluxo maior que 50 MB é interrompido mesmo com tamanho declarado pequeno")
    void streamOverLimitIsAborted() throws IOException {
        MultipartFile liar = Mockito.mock(MultipartFile.class);
        Mockito.when(liar.isEmpty()).thenReturn(false);
        Mockito.when(liar.getSize()).thenReturn(10L);
        Mockito.when(liar.getOriginalFilename()).thenReturn("a.pdf");
        Mockito.when(liar.getInputStream()).thenReturn(new InputStream() {
            long left = ClassroomMaterialStorage.MAX_BYTES + 1;
            @Override public int read() { return left-- > 0 ? 'a' : -1; }
            @Override public int read(byte[] b, int off, int len) {
                if (left <= 0) return -1;
                int n = (int) Math.min(len, left);
                java.util.Arrays.fill(b, off, off + n, (byte) 'a');
                left -= n;
                return n;
            }
        });

        assertEquals("O limite é de 50 MB", message(() -> storage().store(liar)));
        assertEquals(0, filesInRoot());
    }

    @Test
    @DisplayName("Falha de E/S vira 500 e limpa o arquivo")
    void ioFailureBecomesServerError() throws IOException {
        MultipartFile broken = Mockito.mock(MultipartFile.class);
        Mockito.when(broken.isEmpty()).thenReturn(false);
        Mockito.when(broken.getSize()).thenReturn(10L);
        Mockito.when(broken.getOriginalFilename()).thenReturn("a.pdf");
        Mockito.when(broken.getInputStream()).thenThrow(new IOException("disco"));

        MaterialException ex = assertThrows(MaterialException.class, () -> storage().store(broken));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.status());
        assertEquals("Falha ao armazenar arquivo", ex.getMessage());
        assertEquals(0, filesInRoot());
    }

    @Test
    @DisplayName("Fluxo sem bytes lidos resulta em 'Arquivo vazio'")
    void emptyStreamIsRejected() throws IOException {
        MultipartFile empty = Mockito.mock(MultipartFile.class);
        Mockito.when(empty.isEmpty()).thenReturn(false);
        Mockito.when(empty.getSize()).thenReturn(5L);
        Mockito.when(empty.getOriginalFilename()).thenReturn("a.pdf");
        Mockito.when(empty.getInputStream()).thenReturn(InputStream.nullInputStream());

        assertEquals("Arquivo vazio", message(() -> storage().store(empty)));
        assertEquals(0, filesInRoot());
    }

    // sincronização de transação

    @Test
    @DisplayName("Rollback da transação remove o arquivo; commit mantém")
    void rollbackRemovesStoredFile() throws IOException {
        var storage = storage();
        TransactionSynchronizationManager.initSynchronization();
        var committed = storage.store(pdf());
        var rolledBack = storage.store(pdf());
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(2, syncs.size());

        syncs.get(0).afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        syncs.get(1).afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertNotNull(storage.load(committed.name()));
        assertThrows(MaterialException.class, () -> storage.load(rolledBack.name()));
        assertEquals(1, filesInRoot());
    }

    @Test
    @DisplayName("Apagar após commit só executa no afterCommit quando há transação")
    void deleteAfterCommitWaitsForCommit() throws IOException {
        var storage = storage();
        var stored = storage.store(pdf());
        TransactionSynchronizationManager.initSynchronization();

        storage.deleteAfterCommit(List.of(stored.name()));

        assertTrue(storage.load(stored.name()).exists());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThrows(MaterialException.class, () -> storage.load(stored.name()));
    }

    @Test
    @DisplayName("Lista vazia não registra nada")
    void deleteAfterCommitEmptyListIsNoop() {
        TransactionSynchronizationManager.initSynchronization();
        storage().deleteAfterCommit(List.of());
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    @Test
    @DisplayName("Sem transação ativa o arquivo é removido na hora")
    void deleteWithoutTransactionIsImmediate() {
        var storage = storage();
        var stored = storage.store(pdf());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());

        storage.deleteAfterCommit(List.of(stored.name()));

        assertThrows(MaterialException.class, () -> storage.load(stored.name()));
    }

    // load e nomes

    @Test
    @DisplayName("Load recusa nomes fora do padrão e arquivos inexistentes")
    void loadRejectsInvalidNames() {
        var storage = storage();
        assertThrows(MaterialException.class, () -> storage.load(null));
        assertThrows(MaterialException.class, () -> storage.load("../../etc/passwd"));
        assertThrows(MaterialException.class, () -> storage.load("a.pdf"));
        assertThrows(MaterialException.class, () -> storage.load(UUID.randomUUID() + ".pdf"));
    }

    @Test
    @DisplayName("Load recusa link simbólico")
    void loadRejectsSymlink() throws IOException {
        var storage = storage();
        Files.createDirectories(root());
        Path target = Files.writeString(dir.resolve("alvo.txt"), "x");
        String name = UUID.randomUUID() + ".txt";
        try {
            Files.createSymbolicLink(root().resolve(name), target);
        } catch (UnsupportedOperationException | IOException e) {
            return; // sistema de arquivos sem suporte a symlink
        }
        assertThrows(MaterialException.class, () -> storage.load(name));
    }

    @Test
    @DisplayName("Falha ao apagar é registrada sem propagar")
    void deleteFailureIsSwallowed() throws IOException {
        var storage = storage();
        Files.createDirectories(root());
        String name = UUID.randomUUID() + ".pdf";
        // diretório não vazio com nome de arquivo: deleteIfExists lança DirectoryNotEmptyException
        Path folder = Files.createDirectory(root().resolve(name));
        Files.writeString(folder.resolve("filho"), "x");

        storage.deleteAfterCommit(List.of(name));

        assertTrue(Files.exists(folder));
    }
}
