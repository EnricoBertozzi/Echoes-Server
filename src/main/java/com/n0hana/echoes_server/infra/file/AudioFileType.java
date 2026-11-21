package com.n0hana.echoes_server.infra.file;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Formatos de áudio de cenário que o servidor reconhece, com a assinatura do
 * conteúdo. A extensão sozinha não basta: o conteúdo precisa bater com ela.
 *
 * <p>
 * Quais formatos são aceitos em upload é decidido por
 * {@code SCENARIO_AUDIO_EXTENSIONS}; este enum só diz como reconhecer cada um.
 * </p>
 */
public enum AudioFileType {

    MP3("mp3") {
        @Override
        boolean matches(byte[] head) {
            // ID3 (tag no início) ou frame sync 0xFFEx
            boolean id3 = startsWith(head, "ID3");
            boolean frameSync = head.length >= 2
                    && (head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0;
            return id3 || frameSync;
        }
    },

    WAV("wav") {
        @Override
        boolean matches(byte[] head) {
            // RIFF <tamanho> WAVE
            return startsWith(head, "RIFF") && head.length >= 12 && at(head, 8, "WAVE");
        }
    },

    OGG("ogg") {
        @Override
        boolean matches(byte[] head) {
            return startsWith(head, "OggS");
        }
    },

    M4A("m4a") {
        @Override
        boolean matches(byte[] head) {
            // contêiner MP4/ISO: a caixa "ftyp" fica no offset 4
            return head.length >= 8 && at(head, 4, "ftyp");
        }
    };

    private final String extension;

    AudioFileType(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    /** o início do arquivo (até 16 bytes) bate com a assinatura deste formato */
    abstract boolean matches(byte[] head);

    public static Optional<AudioFileType> fromExtension(String extension) {
        if (extension == null)
            return Optional.empty();
        String normalized = extension.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.extension.equals(normalized))
                .findFirst();
    }

    static boolean startsWith(byte[] head, String prefix) {
        return at(head, 0, prefix);
    }

    static boolean at(byte[] head, int offset, String text) {
        byte[] expected = text.getBytes(StandardCharsets.US_ASCII);
        if (head.length < offset + expected.length)
            return false;
        for (int i = 0; i < expected.length; i++) {
            if (head[offset + i] != expected[i])
                return false;
        }
        return true;
    }
}
