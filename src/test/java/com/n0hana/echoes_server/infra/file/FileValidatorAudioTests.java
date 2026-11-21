package com.n0hana.echoes_server.infra.file;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.n0hana.echoes_server.infra.file.exception.FileValidateException;

class FileValidatorAudioTests {

    private final FileValidatorService validator = new FileValidatorService();

    @Test
    @DisplayName("Aceita mp3 com tag ID3 (B3)")
    void acceptsId3Mp3() {
        var file = new MockMultipartFile("file", "aula.mp3", "audio/mpeg", "ID3\u0004\u0000rest".getBytes(StandardCharsets.ISO_8859_1));
        assertDoesNotThrow(() -> validator.validateAudio(file));
    }

    @Test
    @DisplayName("Aceita mp3 sem tag, começando direto no frame sync (B3)")
    void acceptsFrameSyncMp3() {
        var file = new MockMultipartFile("file", "aula.mp3", "audio/mpeg", new byte[]{(byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0x00});
        assertDoesNotThrow(() -> validator.validateAudio(file));
    }

    @Test
    @DisplayName("Rejeita arquivo com extensão mp3 mas conteúdo que não é áudio (B3)")
    void rejectsFakeMp3() {
        var file = new MockMultipartFile("file", "aula.mp3", "audio/mpeg", "<html>nope</html>".getBytes(StandardCharsets.UTF_8));
        assertThrows(FileValidateException.class, () -> validator.validateAudio(file));
    }

    @Test
    @DisplayName("Rejeita extensões que não são mp3 para áudio de cenário (B3)")
    void rejectsNonMp3Extension() {
        var file = new MockMultipartFile("file", "aula.pdf", "application/pdf", "%PDF-1.4".getBytes(StandardCharsets.UTF_8));
        assertThrows(FileValidateException.class, () -> validator.validateAudio(file));
    }

    @Test
    @DisplayName("Aceita WAV, OGG e M4A com assinatura correta")
    void acceptsWavOggAndM4a() {
        byte[] wav = "RIFF\u0000\u0000\u0000\u0000WAVEfmt ".getBytes(StandardCharsets.ISO_8859_1);
        byte[] ogg = "OggS\u0000\u0002".getBytes(StandardCharsets.ISO_8859_1);
        byte[] m4a = "\u0000\u0000\u0000\u0020ftypM4A \u0000\u0000".getBytes(StandardCharsets.ISO_8859_1);

        assertDoesNotThrow(() -> validator.validateAudio(new MockMultipartFile("file", "a.wav", "audio/wav", wav)));
        assertDoesNotThrow(() -> validator.validateAudio(new MockMultipartFile("file", "a.ogg", "audio/ogg", ogg)));
        assertDoesNotThrow(() -> validator.validateAudio(new MockMultipartFile("file", "a.m4a", "audio/mp4", m4a)));
    }

    @Test
    @DisplayName("Rejeita conteúdo de outro formato com a extensão declarada")
    void rejectsContentFromAnotherFormat() {
        byte[] ogg = "OggS\u0000\u0002".getBytes(StandardCharsets.ISO_8859_1);
        assertThrows(FileValidateException.class, () ->
                validator.validateAudio(new MockMultipartFile("file", "a.wav", "audio/wav", ogg)));
        assertThrows(FileValidateException.class, () ->
                validator.validateAudio(new MockMultipartFile("file", "a.mp3", "audio/mpeg", ogg)));
    }

    @Test
    @DisplayName("Rejeita extensão fora da lista configurada")
    void rejectsExtensionOutsideConfiguredList() {
        byte[] flac = "fLaC\u0000\u0000".getBytes(StandardCharsets.ISO_8859_1);
        assertThrows(FileValidateException.class, () ->
                validator.validateAudio(new MockMultipartFile("file", "a.flac", "audio/flac", flac)));
    }
}
