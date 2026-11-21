package com.n0hana.echoes_server.classroom.material;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.n0hana.echoes_server.user.model.Teacher;

class ClassroomMaterialInfoCoverageTests {

    @Test
    @DisplayName("Autor ativo sem nome também aparece como 'Usuário removido'")
    void activeAuthorWithoutNameIsMasked() {
        Teacher author = new Teacher();
        author.setId(UUID.randomUUID());
        author.setActive(true);
        author.setName(null);
        ClassroomMaterial m = new ClassroomMaterial();
        m.setId(UUID.randomUUID());
        m.setAuthor(author);
        m.setTitle("T");
        m.setOriginalFileName("a.docx");
        m.setContentType("x");

        ClassroomMaterialDTO.Info info = ClassroomMaterialDTO.Info.from(m);

        assertEquals(ClassroomMaterialDTO.REMOVED_AUTHOR, info.authorName());
        assertEquals(false, info.previewable());
        assertEquals(author.getId(), info.authorId());
    }
}
