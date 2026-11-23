package com.n0hana.echoes_server.infra.file;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import com.n0hana.echoes_server.infra.file.exception.FileValidateException;

class FileValidatorServiceCoverageTests {

    private final FileValidatorService validator = new FileValidatorService();

    private static final byte[] WAV = "RIFF\0\0\0\0WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

    @Test
    @DisplayName("Arquivo nulo ou vazio é inválido")
    void nullOrEmpty() {
        List<String> ext = List.of("pdf");
        assertThrows(FileValidateException.class, () -> validator.validate(null, ext));
        assertThrows(FileValidateException.class,
                () -> validator.validate(new MockMultipartFile("f", "a.pdf", "x", new byte[0]), ext));
    }

    @Test
    @DisplayName("Arquivo acima de 50 MB é inválido")
    void tooLarge() {
        MultipartFile big = mock(MultipartFile.class);
        when(big.isEmpty()).thenReturn(false);
        when(big.getSize()).thenReturn(50L * 1024 * 1024 + 1);

        assertThrows(FileValidateException.class, () -> validator.validate(big, List.of("pdf")));
    }

    @Test
    @DisplayName("Exatamente 50 MB ainda é aceito")
    void exactLimitAccepted() {
        MultipartFile limit = mock(MultipartFile.class);
        when(limit.isEmpty()).thenReturn(false);
        when(limit.getSize()).thenReturn(50L * 1024 * 1024);
        when(limit.getOriginalFilename()).thenReturn("a.pdf");

        assertDoesNotThrow(() -> validator.validate(limit, List.of("pdf")));
    }

    @Test
    @DisplayName("Nome com travessia de diretório é inválido")
    void pathTraversal() {
        List<String> ext = List.of("pdf");
        for (String name : new String[] { "a..b.pdf", "x\\a.pdf", "../a.pdf" }) {
            assertThrows(FileValidateException.class,
                    () -> validator.validate(new MockMultipartFile("f", name, "x", new byte[] { 1 }), ext),
                    name);
        }
    }

    @Test
    @DisplayName("Arquivo sem extensão é inválido")
    void noExtension() {
        assertThrows(FileValidateException.class,
                () -> validator.validate(new MockMultipartFile("f", "semextensao", "x", new byte[] { 1 }),
                        List.of("pdf")));
    }

    @Test
    @DisplayName("validateAudio aceita WAV e OGG/M4A válidos pela configuração padrão")
    void audioAcceptsConfiguredFormats() {
        assertDoesNotThrow(() -> validator.validateAudio(new MockMultipartFile("f", "a.wav", "audio/wav", WAV)));
        assertDoesNotThrow(() -> validator.validateAudio(
                new MockMultipartFile("f", "a.ogg", "audio/ogg", "OggS\0\2".getBytes())));
    }

    @Test
    @DisplayName("validateAudio recusa extensão sem tipo de áudio conhecido mesmo se configurada")
    void audioRejectsUnknownTypeEvenIfConfigured() {
        ReflectionTestUtils.setField(validator, "audioExtensions", List.of("flac"));

        assertThrows(FileValidateException.class,
                () -> validator.validateAudio(new MockMultipartFile("f", "a.flac", "audio/flac", WAV)));
    }

    @Test
    @DisplayName("validateAudio recusa assinatura que não bate com a extensão")
    void audioRejectsWrongSignature() {
        assertThrows(FileValidateException.class,
                () -> validator.validateAudio(new MockMultipartFile("f", "a.ogg", "audio/ogg", WAV)));
    }

    @Test
    @DisplayName("validateAudio recusa quando a leitura do conteúdo falha")
    void audioRejectsUnreadableContent() throws IOException {
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.isEmpty()).thenReturn(false);
        when(broken.getSize()).thenReturn(10L);
        when(broken.getOriginalFilename()).thenReturn("a.mp3");
        when(broken.getInputStream()).thenThrow(new IOException("falha"));

        assertThrows(FileValidateException.class, () -> validator.validateAudio(broken));
    }
}
