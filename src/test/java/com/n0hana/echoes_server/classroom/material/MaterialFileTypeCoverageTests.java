package com.n0hana.echoes_server.classroom.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MaterialFileTypeCoverageTests {

    @TempDir Path dir;

    private Path write(byte[] bytes) throws IOException {
        Path file = dir.resolve("arquivo-" + System.nanoTime());
        Files.write(file, bytes);
        return file;
    }

    private static byte[] ascii(String s) { return s.getBytes(StandardCharsets.US_ASCII); }

    private static byte[] zip(String... entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (String name : entries) {
                out.putNextEntry(new ZipEntry(name));
                out.write(1);
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private void assertValid(MaterialFileType type, byte[] content) throws IOException {
        type.validateContent(write(content));
    }

    private void assertInvalid(MaterialFileType type, byte[] content) throws IOException {
        Path file = write(content);
        MaterialException ex = assertThrows(MaterialException.class, () -> type.validateContent(file));
        assertEquals("Conteúdo incompatível com a extensão do arquivo", ex.getMessage());
    }

    // fromFilename

    @Test
    @DisplayName("Resolve o tipo pela extensão sem diferenciar maiúsculas")
    void resolvesTypeFromExtension() {
        assertEquals(MaterialFileType.PDF, MaterialFileType.fromFilename("Aula.PDF"));
        assertEquals(MaterialFileType.JPEG, MaterialFileType.fromFilename("foto.jpeg"));
        assertEquals(MaterialFileType.MD, MaterialFileType.fromFilename("a.b.md"));
        assertEquals(MaterialFileType.XLSX, MaterialFileType.fromFilename("planilha.xlsx"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "a/b.pdf", "a\\b.pdf", "a..b.pdf", "a\u0001.pdf", "a\u007f.pdf" })
    @DisplayName("Nomes inválidos são recusados")
    void rejectsInvalidNames(String name) {
        MaterialException ex = assertThrows(MaterialException.class, () -> MaterialFileType.fromFilename(name));
        assertEquals("Nome de arquivo inválido", ex.getMessage());
    }

    @Test
    @DisplayName("Nome com mais de 255 caracteres é recusado")
    void rejectsTooLongName() {
        String name = "a".repeat(252) + ".pdf";
        assertThrows(MaterialException.class, () -> MaterialFileType.fromFilename(name));
        assertEquals(MaterialFileType.PDF, MaterialFileType.fromFilename("a".repeat(251) + ".pdf"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "semextensao", ".pdf", "termina." })
    @DisplayName("Nome sem extensão utilizável é recusado")
    void rejectsMissingExtension(String name) {
        MaterialException ex = assertThrows(MaterialException.class, () -> MaterialFileType.fromFilename(name));
        assertEquals("Arquivo sem extensão permitida", ex.getMessage());
    }

    @Test
    @DisplayName("Extensão desconhecida é recusada")
    void rejectsUnknownExtension() {
        MaterialException ex = assertThrows(MaterialException.class, () -> MaterialFileType.fromFilename("a.exe"));
        assertEquals("Extensão não permitida", ex.getMessage());
    }

    @Test
    @DisplayName("Accessors e pré-visualização por tipo")
    void accessors() {
        assertTrue(MaterialFileType.PDF.previewable());
        assertTrue(MaterialFileType.WEBP.previewable());
        assertFalse(MaterialFileType.DOCX.previewable());
        assertFalse(MaterialFileType.TXT.previewable());
        assertEquals("pdf", MaterialFileType.PDF.extension());
        assertEquals("image/jpeg", MaterialFileType.JPG.contentType());
        assertEquals("text/csv; charset=UTF-8", MaterialFileType.CSV.contentType());
    }

    // assinaturas

    @Test
    @DisplayName("Arquivo vazio é recusado")
    void emptyFileIsRejected() throws IOException {
        Path file = write(new byte[0]);
        MaterialException ex = assertThrows(MaterialException.class, () -> MaterialFileType.PDF.validateContent(file));
        assertEquals("Arquivo vazio", ex.getMessage());
    }

    @Test
    @DisplayName("PDF exige %PDF-")
    void pdfSignature() throws IOException {
        assertValid(MaterialFileType.PDF, ascii("%PDF-1.7 corpo"));
        assertInvalid(MaterialFileType.PDF, ascii("%PDX-1.7"));
        assertInvalid(MaterialFileType.PDF, ascii("%PD"));
    }

    @Test
    @DisplayName("PNG exige a assinatura de 8 bytes")
    void pngSignature() throws IOException {
        byte[] png = { (byte) 0x89, 80, 78, 71, 13, 10, 26, 10, 0 };
        assertValid(MaterialFileType.PNG, png);
        assertInvalid(MaterialFileType.PNG, new byte[] { (byte) 0x89, 80, 78, 71, 13, 10, 26, 11 });
        assertInvalid(MaterialFileType.PNG, new byte[] { (byte) 0x89, 80, 78 });
    }

    @Test
    @DisplayName("JPG e JPEG exigem FF D8 FF")
    void jpegSignature() throws IOException {
        byte[] jpg = { (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0 };
        assertValid(MaterialFileType.JPG, jpg);
        assertValid(MaterialFileType.JPEG, jpg);
        assertInvalid(MaterialFileType.JPG, new byte[] { (byte) 0xff, (byte) 0xd8, 0x00 });
        assertInvalid(MaterialFileType.JPEG, new byte[] { 1, 2, 3 });
    }

    @Test
    @DisplayName("GIF aceita GIF87a e GIF89a")
    void gifSignature() throws IOException {
        assertValid(MaterialFileType.GIF, ascii("GIF87a.."));
        assertValid(MaterialFileType.GIF, ascii("GIF89a.."));
        assertInvalid(MaterialFileType.GIF, ascii("GIF90a.."));
    }

    @Test
    @DisplayName("WEBP exige RIFF + WEBP no offset 8 e ao menos 12 bytes")
    void webpSignature() throws IOException {
        assertValid(MaterialFileType.WEBP, concat(ascii("RIFF\0\0\0\0WEBP"), ascii("VP8 ")));
        assertInvalid(MaterialFileType.WEBP, ascii("RIFF\0\0\0\0WAVE"));
        assertInvalid(MaterialFileType.WEBP, ascii("RIFFxxxx"));
        assertInvalid(MaterialFileType.WEBP, ascii("XXXX\0\0\0\0WEBP"));
    }

    @Test
    @DisplayName("Texto puro rejeita bytes de controle e NUL")
    void plainTextSignature() throws IOException {
        byte[] ok = "linha 1\r\n\tlinha 2 ação\n".getBytes(StandardCharsets.UTF_8);
        for (MaterialFileType type : new MaterialFileType[] { MaterialFileType.TXT, MaterialFileType.CSV,
                MaterialFileType.MD }) {
            assertValid(type, ok);
            assertInvalid(type, new byte[] { 'a', 0, 'b' });
            assertInvalid(type, new byte[] { 'a', 5 });
            assertInvalid(type, new byte[] { 'a', 20 });
            assertInvalid(type, new byte[] { 'a', 127 });
        }
    }

    @Test
    @DisplayName("Office exige pacote ZIP com [Content_Types].xml e a parte específica")
    void officeZipValidation() throws IOException {
        assertValid(MaterialFileType.DOCX, zip("[Content_Types].xml", "word/document.xml"));
        assertValid(MaterialFileType.PPTX, zip("[Content_Types].xml", "ppt/presentation.xml"));
        assertValid(MaterialFileType.XLSX, zip("[Content_Types].xml", "xl/workbook.xml"));

        // sem [Content_Types].xml
        assertInvalid(MaterialFileType.DOCX, zip("word/document.xml"));
        // parte específica de outro formato
        assertInvalid(MaterialFileType.DOCX, zip("[Content_Types].xml", "xl/workbook.xml"));
        assertInvalid(MaterialFileType.PPTX, zip("[Content_Types].xml", "word/document.xml"));
        assertInvalid(MaterialFileType.XLSX, zip("[Content_Types].xml", "ppt/presentation.xml"));
        // não é ZIP
        assertInvalid(MaterialFileType.DOCX, ascii("isto não é um zip de verdade"));
    }

    @Test
    @DisplayName("Pacote Office com mais de 10000 entradas é recusado")
    void officeZipWithTooManyEntries() throws IOException {
        String[] names = new String[10002];
        names[0] = "[Content_Types].xml";
        names[1] = "word/document.xml";
        for (int i = 2; i < names.length; i++) names[i] = "x/" + i;
        assertInvalid(MaterialFileType.DOCX, zip(names));
    }
}
