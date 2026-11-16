package com.n0hana.echoes_server.classroom.material;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MaterialFileTypeTests {
    @TempDir Path tempDir;

    @Test void rejectsDangerousExtensionsAndPaths() {
        assertThrows(MaterialException.class, () -> MaterialFileType.fromFilename("../a.pdf"));
        assertThrows(MaterialException.class, () -> MaterialFileType.fromFilename("x.html"));
        assertThrows(MaterialException.class, () -> MaterialFileType.fromFilename("x.pdf\\r\\n"));
    }

    @Test void validatesPdfSignature() throws Exception {
        Path valid = tempDir.resolve("valid.pdf");
        Files.writeString(valid, "%PDF-1.7\n");
        assertDoesNotThrow(() -> MaterialFileType.PDF.validateContent(valid));
        Path fake = tempDir.resolve("fake.pdf");
        Files.writeString(fake, "<script>alert('x')</script>");
        assertThrows(MaterialException.class, () -> MaterialFileType.PDF.validateContent(fake));
    }
}
