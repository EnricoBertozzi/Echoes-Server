package com.n0hana.echoes_server.infra.file.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FileStorageExceptionHandlerCoverageTests {

    private final FileStorageExceptionHandler handler = new FileStorageExceptionHandler();

    @Test
    @DisplayName("Arquivo inválido responde 400 com a mensagem")
    void validate() {
        var r = handler.handleValidate(new FileValidateException());
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertEquals("Arquivo Inválido", r.getBody().get("message"));
    }

    @Test
    @DisplayName("Arquivo não encontrado responde 404")
    void notFound() {
        var r = handler.handleNotFound(new StorageFileNotFoundException());
        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        assertEquals("Arquivo não encontrado", r.getBody().get("message"));
    }

    @Test
    @DisplayName("Falhas de gravação e exclusão respondem 500")
    void storageFailures() {
        var save = handler.handleStorage(new StorageSaveFileException());
        var delete = handler.handleStorage(new StorageDeleteFileException());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, save.getStatusCode());
        assertEquals("Falha ao salvar o arquivo", save.getBody().get("message"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, delete.getStatusCode());
        assertEquals("Falha ao deletar o arquivo", delete.getBody().get("message"));
    }
}
