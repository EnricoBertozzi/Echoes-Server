package com.n0hana.echoes_server.infra.file;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import com.n0hana.echoes_server.infra.file.exception.FileValidateException;

class FileValidatorServiceTests {

    private final FileValidatorService validator = new FileValidatorService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(validator, "ALLOWED_EXTENSIONS", List.of("mp3", "wav"));
    }

    @Test
    @DisplayName("Aceita extensão da lista global")
    void acceptsGlobalExtension() {
        assertDoesNotThrow(() -> validator.validate(file("audio.mp3", "conteudo")));
    }

    @Test
    @DisplayName("Rejeita extensão fora da lista global")
    void rejectsExtensionOutsideGlobalList() {
        assertThrows(FileValidateException.class, () -> validator.validate(file("aula.pdf", "conteudo")));
    }

    @Test
    @DisplayName("Aceita extensão da lista informada, ignorando maiúsculas")
    void acceptsExtensionOfGivenList() {
        assertDoesNotThrow(() -> validator.validate(file("Aula.PDF", "conteudo"), List.of("pdf", "docx")));
    }

    @Test
    @DisplayName("Rejeita extensão fora da lista informada")
    void rejectsExtensionOutsideGivenList() {
        assertThrows(FileValidateException.class,
                () -> validator.validate(file("audio.mp3", "conteudo"), List.of("pdf", "docx")));
    }

    @Test
    @DisplayName("Rejeita arquivo sem extensão")
    void rejectsFileWithoutExtension() {
        assertThrows(FileValidateException.class,
                () -> validator.validate(file("arquivo", "conteudo"), List.of("pdf")));
    }

    @Test
    @DisplayName("Rejeita arquivo vazio")
    void rejectsEmptyFile() {
        assertThrows(FileValidateException.class,
                () -> validator.validate(file("aula.pdf", ""), List.of("pdf")));
    }

    @Test
    @DisplayName("Rejeita nome com caminho relativo")
    void rejectsPathTraversalName() {
        assertThrows(FileValidateException.class,
                () -> validator.validate(file("../aula.pdf", "conteudo"), List.of("pdf")));
    }

    @Test
    @DisplayName("Rejeita arquivo maior que 50 MB")
    void rejectsFileTooLarge() {
        MultipartFile big = mock(MultipartFile.class);
        when(big.isEmpty()).thenReturn(false);
        when(big.getSize()).thenReturn(50L * 1024L * 1024L + 1);

        assertThrows(FileValidateException.class, () -> validator.validate(big, List.of("pdf")));
    }

    private MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content.getBytes());
    }
}
