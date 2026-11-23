package com.n0hana.echoes_server.classroom.material;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.n0hana.echoes_server.user.model.Teacher;

class ClassroomMaterialDtoTests {

    private ClassroomMaterial material(Teacher author) {
        ClassroomMaterial m = new ClassroomMaterial();
        m.setId(UUID.randomUUID());
        m.setAuthor(author);
        m.setTitle("Aula");
        m.setOriginalFileName("aula.pdf");
        m.setContentType("application/pdf");
        m.setFileSize(1L);
        return m;
    }

    private Teacher teacher(String name, boolean active) {
        Teacher t = new Teacher();
        t.setId(UUID.randomUUID());
        t.setName(name);
        t.setActive(active);
        return t;
    }

    @Test
    @DisplayName("Autor ativo aparece pelo nome")
    void activeAuthorKeepsName() {
        assertEquals("Maria", ClassroomMaterialDTO.Info.from(material(teacher("Maria", true))).authorName());
    }

    @Test
    @DisplayName("Autor excluído (RF06) aparece como 'Usuário removido', mesmo se o nome ainda existir")
    void removedAuthorIsMasked() {
        assertEquals(ClassroomMaterialDTO.REMOVED_AUTHOR,
                ClassroomMaterialDTO.Info.from(material(teacher(null, false))).authorName());
        assertEquals(ClassroomMaterialDTO.REMOVED_AUTHOR,
                ClassroomMaterialDTO.Info.from(material(teacher("Maria", false))).authorName());
    }
}
