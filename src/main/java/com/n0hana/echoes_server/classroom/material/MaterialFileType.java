package com.n0hana.echoes_server.classroom.material;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.zip.ZipFile;

/** Lista independente da permissão de extensões dos áudios de cenário. */
public enum MaterialFileType {
    PDF("pdf", "application/pdf", true),
    PNG("png", "image/png", true),
    JPG("jpg", "image/jpeg", true),
    JPEG("jpeg", "image/jpeg", true),
    GIF("gif", "image/gif", true),
    WEBP("webp", "image/webp", true),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", false),
    PPTX("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", false),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", false),
    TXT("txt", "text/plain; charset=UTF-8", false),
    CSV("csv", "text/csv; charset=UTF-8", false),
    MD("md", "text/markdown; charset=UTF-8", false);

    private final String extension;
    private final String contentType;
    private final boolean previewable;

    MaterialFileType(String extension, String contentType, boolean previewable) {
        this.extension = extension;
        this.contentType = contentType;
        this.previewable = previewable;
    }

    public String extension() { return extension; }
    public String contentType() { return contentType; }
    public boolean previewable() { return previewable; }

    public static MaterialFileType fromFilename(String filename) {
        if (filename == null || filename.length() > 255 || filename.isBlank()
                || filename.contains("/") || filename.contains("\\") || filename.contains("..")
                || filename.chars().anyMatch(ch -> ch < 32 || ch == 127)) {
            throw MaterialException.invalid("Nome de arquivo inválido");
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 1 || dot == filename.length() - 1) {
            throw MaterialException.invalid("Arquivo sem extensão permitida");
        }
        String suffix = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(t -> t.extension.equals(suffix)).findFirst()
                .orElseThrow(() -> MaterialException.invalid("Extensão não permitida"));
    }

    public void validateContent(Path file) throws IOException {
        byte[] head;
        try (InputStream in = Files.newInputStream(file)) {
            head = in.readNBytes(8192);
        }
        if (head.length == 0) throw MaterialException.invalid("Arquivo vazio");
        boolean matches = switch (this) {
            case PDF -> starts(head, "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            case PNG -> starts(head, new byte[]{(byte) 0x89, 80, 78, 71, 13, 10, 26, 10});
            case JPG, JPEG -> starts(head, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
            case GIF -> starts(head, "GIF87a".getBytes()) || starts(head, "GIF89a".getBytes());
            case WEBP -> head.length >= 12 && starts(head, "RIFF".getBytes())
                    && new String(head, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
            case DOCX, PPTX, XLSX -> validateOfficeZip(file);
            case TXT, CSV, MD -> isPlainText(head);
        };
        if (!matches) throw MaterialException.invalid("Conteúdo incompatível com a extensão do arquivo");
    }

    private boolean validateOfficeZip(Path file) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            if (zip.getEntry("[Content_Types].xml") == null) return false;
            String specific = switch (this) {
                case DOCX -> "word/document.xml";
                case PPTX -> "ppt/presentation.xml";
                case XLSX -> "xl/workbook.xml";
                default -> throw new IllegalStateException();
            };
            if (zip.size() > 10000) return false;
            return zip.getEntry(specific) != null;
        } catch (java.util.zip.ZipException ex) {
            return false;
        }
    }

    private static boolean isPlainText(byte[] head) {
        for (byte b : head) {
            int c = b & 0xff;
            if (c == 0 || c < 9 || (c > 13 && c < 32) || c == 127) return false;
        }
        return true;
    }

    private static boolean starts(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (bytes[i] != prefix[i]) return false;
        return true;
    }
}
