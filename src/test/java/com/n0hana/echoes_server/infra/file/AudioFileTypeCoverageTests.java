package com.n0hana.echoes_server.infra.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AudioFileTypeCoverageTests {

    private static byte[] ascii(String s) { return s.getBytes(StandardCharsets.US_ASCII); }

    @Test
    @DisplayName("fromExtension ignora maiúsculas e devolve vazio para nulo/desconhecido")
    void fromExtension() {
        assertEquals(Optional.of(AudioFileType.MP3), AudioFileType.fromExtension("MP3"));
        assertEquals(Optional.of(AudioFileType.WAV), AudioFileType.fromExtension("wav"));
        assertEquals(Optional.of(AudioFileType.OGG), AudioFileType.fromExtension("Ogg"));
        assertEquals(Optional.of(AudioFileType.M4A), AudioFileType.fromExtension("m4a"));
        assertEquals(Optional.empty(), AudioFileType.fromExtension("flac"));
        assertEquals(Optional.empty(), AudioFileType.fromExtension(null));
        assertEquals("m4a", AudioFileType.M4A.extension());
    }

    @Test
    @DisplayName("MP3: ID3 ou frame sync; rejeita o resto")
    void mp3Signature() {
        assertTrue(AudioFileType.MP3.matches(ascii("ID3\4\0")));
        assertTrue(AudioFileType.MP3.matches(new byte[] { (byte) 0xFF, (byte) 0xFB }));
        assertTrue(AudioFileType.MP3.matches(new byte[] { (byte) 0xFF, (byte) 0xE0 }));
        assertFalse(AudioFileType.MP3.matches(new byte[] { (byte) 0xFF, (byte) 0x1B }));
        assertFalse(AudioFileType.MP3.matches(new byte[] { 0x00, (byte) 0xFB }));
        assertFalse(AudioFileType.MP3.matches(new byte[] { (byte) 0xFF }));
        assertFalse(AudioFileType.MP3.matches(new byte[0]));
        assertFalse(AudioFileType.MP3.matches(ascii("ID")));
    }

    @Test
    @DisplayName("WAV: RIFF + WAVE no offset 8")
    void wavSignature() {
        assertTrue(AudioFileType.WAV.matches(ascii("RIFF\0\0\0\0WAVEfmt ")));
        assertFalse(AudioFileType.WAV.matches(ascii("RIFF\0\0\0\0AVI ")));
        assertFalse(AudioFileType.WAV.matches(ascii("RIFF\0\0\0\0WAV")));
        assertFalse(AudioFileType.WAV.matches(ascii("RIFX\0\0\0\0WAVE")));
        assertFalse(AudioFileType.WAV.matches(ascii("RIFF")));
    }

    @Test
    @DisplayName("OGG: OggS no início")
    void oggSignature() {
        assertTrue(AudioFileType.OGG.matches(ascii("OggS\0\2")));
        assertFalse(AudioFileType.OGG.matches(ascii("Ogg")));
        assertFalse(AudioFileType.OGG.matches(ascii("OGGS")));
    }

    @Test
    @DisplayName("M4A: caixa ftyp no offset 4")
    void m4aSignature() {
        assertTrue(AudioFileType.M4A.matches(concat(new byte[] { 0, 0, 0, 0x20 }, ascii("ftypM4A "))));
        assertFalse(AudioFileType.M4A.matches(concat(new byte[] { 0, 0, 0, 0x20 }, ascii("moov"))));
        assertFalse(AudioFileType.M4A.matches(ascii("ftyp")));
        assertFalse(AudioFileType.M4A.matches(new byte[7]));
    }

    @Test
    @DisplayName("Helpers startsWith/at conferem o offset")
    void helpers() {
        assertTrue(AudioFileType.startsWith(ascii("abc"), "ab"));
        assertFalse(AudioFileType.startsWith(ascii("abc"), "abd"));
        assertTrue(AudioFileType.at(ascii("xxabc"), 2, "abc"));
        assertFalse(AudioFileType.at(ascii("xxab"), 2, "abc"));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
