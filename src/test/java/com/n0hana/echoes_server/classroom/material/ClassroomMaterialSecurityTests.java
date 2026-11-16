package com.n0hana.echoes_server.classroom.material;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.n0hana.echoes_server.classroom.ClassroomService;
import com.n0hana.echoes_server.classroom.exception.ClassroomForbiddenException;
import com.n0hana.echoes_server.classroom.exception.ClassroomArchivedException;
import com.n0hana.echoes_server.user.UserRepository;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ClassroomMaterialSecurityTests {
    @Mock ClassroomService classrooms;
    @Mock ClassroomMaterialRepository materials;
    @Mock ClassroomMaterialStorage storage;
    @Mock UserRepository users;
    @InjectMocks ClassroomMaterialService service;

    private final UUID classroom = UUID.randomUUID();

    @Test void rejectsTeacherWhoIsNotClassroomMemberBeforeStoring() {
        when(classrooms.requireTeacherForMaterials("teacher@example.com", classroom))
                .thenThrow(new ClassroomForbiddenException("Sem acesso"));
        assertThrows(ClassroomForbiddenException.class, () -> service.create(
                "teacher@example.com", classroom, "Título", "Descrição",
                new MockMultipartFile("file", "doc.pdf", "application/pdf", "%PDF-1.7".getBytes())));
        verifyNoInteractions(materials, storage, users);
    }

    @Test void archivedClassroomCannotBeEdited() {
        UUID material = UUID.randomUUID();
        when(classrooms.requireTeacherForMaterials("teacher@example.com", classroom))
                .thenThrow(new ClassroomArchivedException());
        assertThrows(ClassroomArchivedException.class, () -> service.update(
                "teacher@example.com", classroom, material,
                new ClassroomMaterialDTO.Update("Novo título", "Descrição")));
        verifyNoInteractions(materials, storage);
    }

    @Test void nonMemberCannotDownload() {
        UUID material = UUID.randomUUID();
        when(classrooms.findById("person@example.com", classroom))
                .thenThrow(new ClassroomForbiddenException("Sem acesso"));
        assertThrows(ClassroomForbiddenException.class,
                () -> service.download("person@example.com", classroom, material));
        verifyNoInteractions(materials, storage);
    }
}
