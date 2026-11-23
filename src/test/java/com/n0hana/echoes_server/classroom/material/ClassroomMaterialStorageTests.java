package com.n0hana.echoes_server.classroom.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import com.n0hana.echoes_server.infra.file.FileStorageProperties;

class ClassroomMaterialStorageTests {
    @TempDir Path dir;

    private ClassroomMaterialStorage storage() {
        FileStorageProperties props = new FileStorageProperties();
        props.setUploadDir(dir.toString());
        return new ClassroomMaterialStorage(props, java.util.List.of("pdf", "docx", "jpg", "jpeg", "png"));
    }

    @Test void storesPdfUnderIsolatedRandomName() throws Exception {
        var storage = storage();
        var data = new MockMultipartFile("file", "aula.pdf", "application/pdf", "%PDF-1.7\n".getBytes());
        var stored = storage.store(data);
        assertEquals("application/pdf", stored.contentType());
        assertEquals("aula.pdf", stored.originalName());
        assertTrue(stored.name().matches("[0-9a-f-]{36}\\.pdf"));
        assertTrue(storage.load(stored.name()).exists());
        storage.deleteAfterCommit(java.util.List.of(stored.name()));
        assertThrows(MaterialException.class, () -> storage.load(stored.name()));
    }

    @Test void deniesDisguisedExeAndPathTraversal() {
        var storage = storage();
        assertThrows(MaterialException.class, () -> storage.store(
                new MockMultipartFile("file", "bad.pdf", "application/pdf", "MZpayload".getBytes())));
        assertThrows(MaterialException.class, () -> storage.store(
                new MockMultipartFile("file", "../bad.pdf", "application/pdf", "%PDF-1.7".getBytes())));
    }

    @Test void rejectsTypeOutsideConfiguredMaterialExtensions() {
        // gif é reconhecido pelo enum, mas não está em MATERIAL_EXTENSIONS_ALLOWED
        var storage = storage();
        var gif = new MockMultipartFile("file", "aula.gif", "image/gif", "GIF89a".getBytes());
        assertThrows(MaterialException.class, () -> storage.store(gif));
    }
}
