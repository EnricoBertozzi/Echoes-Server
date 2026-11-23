package com.n0hana.echoes_server.classroom.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import com.n0hana.echoes_server.classroom.ClassroomModel;
import com.n0hana.echoes_server.classroom.ClassroomService;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.Teacher;

@ExtendWith(MockitoExtension.class)
class ClassroomMaterialServiceCoverageTests {

    private static final String EMAIL = "professor@exemplo.test";

    @Mock ClassroomService classrooms;
    @Mock ClassroomMaterialRepository repository;
    @Mock ClassroomMaterialStorage storage;
    @Mock UserRepository users;
    @InjectMocks ClassroomMaterialService service;

    private final UUID classroomId = UUID.randomUUID();
    private final UUID materialId = UUID.randomUUID();
    private final InstitutionModel institution = InstitutionModel.builder().id(UUID.randomUUID()).build();
    private final ClassroomModel classroom = ClassroomModel.builder().id(classroomId).title("Turma")
            .period("2026/01").institution(institution).build();
    private final MockMultipartFile file = new MockMultipartFile("file", "aula.pdf", "application/pdf",
            "%PDF-1".getBytes());

    private Teacher teacher() {
        Teacher t = new Teacher();
        t.setId(UUID.randomUUID());
        t.setName("Maria");
        t.setActive(true);
        return t;
    }

    private ClassroomMaterial material(String stored) {
        ClassroomMaterial m = new ClassroomMaterial();
        m.setId(materialId);
        m.setClassroom(classroom);
        m.setAuthor(teacher());
        m.setTitle("Aula");
        m.setDescription("d");
        m.setOriginalFileName("aula.pdf");
        m.setStoredFileName(stored);
        m.setContentType("application/pdf");
        m.setFileSize(6);
        return m;
    }

    private ClassroomMaterialStorage.Stored stored(String name) {
        return new ClassroomMaterialStorage.Stored(name, "nova.docx", "application/x-doc", 42L);
    }

    // list

    @Test
    @DisplayName("Lista confere o acesso e converte para DTO")
    void listMapsToInfo() {
        when(repository.findByClassroomIdOrderByCreatedAtDesc(classroomId)).thenReturn(List.of(material("a.pdf")));

        List<ClassroomMaterialDTO.Info> result = service.list(EMAIL, classroomId);

        verify(classrooms).findById(EMAIL, classroomId);
        assertEquals(1, result.size());
        assertEquals("Aula", result.get(0).title());
        assertTrue(result.get(0).previewable());
    }

    // create

    @Test
    @DisplayName("Cria material: normaliza título/descrição e aplica os dados do arquivo")
    void createStoresAndSaves() {
        Teacher author = teacher();
        when(classrooms.requireTeacherForMaterials(EMAIL, classroomId)).thenReturn(classroom);
        when(storage.store(file)).thenReturn(stored("uuid.docx"));
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(author));
        when(repository.save(any(ClassroomMaterial.class))).thenAnswer(i -> i.getArgument(0));

        ClassroomMaterial result = service.create(EMAIL, classroomId, "  Título  ", null, file);

        assertEquals("Título", result.getTitle());
        assertEquals("", result.getDescription());
        assertSame(classroom, result.getClassroom());
        assertSame(author, result.getAuthor());
        assertEquals("uuid.docx", result.getStoredFileName());
        assertEquals("nova.docx", result.getOriginalFileName());
        assertEquals("application/x-doc", result.getContentType());
        assertEquals(42L, result.getFileSize());
    }

    @Test
    @DisplayName("Descrição é aparada")
    void createTrimsDescription() {
        when(classrooms.requireTeacherForMaterials(EMAIL, classroomId)).thenReturn(classroom);
        when(storage.store(file)).thenReturn(stored("uuid.docx"));
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(teacher()));
        when(repository.save(any(ClassroomMaterial.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals("texto", service.create(EMAIL, classroomId, "T", "  texto ", file).getDescription());
    }

    @Test
    @DisplayName("Título nulo, em branco ou longo demais é recusado antes de gravar o arquivo")
    void createRejectsInvalidTitle() {
        when(classrooms.requireTeacherForMaterials(EMAIL, classroomId)).thenReturn(classroom);

        for (String title : new String[] { null, "   ", "a".repeat(151) }) {
            MaterialException ex = assertThrows(MaterialException.class,
                    () -> service.create(EMAIL, classroomId, title, "d", file));
            assertEquals(HttpStatus.BAD_REQUEST, ex.status());
        }
        assertEquals("a".repeat(150), "a".repeat(150));
        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("Descrição com mais de 1000 caracteres é recusada")
    void createRejectsLongDescription() {
        when(classrooms.requireTeacherForMaterials(EMAIL, classroomId)).thenReturn(classroom);

        MaterialException ex = assertThrows(MaterialException.class,
                () -> service.create(EMAIL, classroomId, "T", "d".repeat(1001), file));

        assertEquals("Descrição com mais de 1000 caracteres", ex.getMessage());
        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("Autor inexistente falha com UserNotFoundException e não salva")
    void createFailsWhenAuthorMissing() {
        when(classrooms.requireTeacherForMaterials(EMAIL, classroomId)).thenReturn(classroom);
        when(storage.store(file)).thenReturn(stored("uuid.docx"));
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.create(EMAIL, classroomId, "T", "d", file));
        verify(repository, never()).save(any());
    }

    // update

    @Test
    @DisplayName("Atualiza título e descrição")
    void updateChangesFields() {
        ClassroomMaterial existing = material("a.pdf");
        when(repository.findByIdAndClassroomId(materialId, classroomId)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        ClassroomMaterial result = service.update(EMAIL, classroomId, materialId,
                new ClassroomMaterialDTO.Update(" Novo ", null));

        verify(classrooms).requireTeacherForMaterials(EMAIL, classroomId);
        assertEquals("Novo", result.getTitle());
        assertEquals("", result.getDescription());
    }

    @Test
    @DisplayName("Atualizar material inexistente devolve 404")
    void updateMissingMaterial() {
        when(repository.findByIdAndClassroomId(materialId, classroomId)).thenReturn(Optional.empty());

        MaterialException ex = assertThrows(MaterialException.class,
                () -> service.update(EMAIL, classroomId, materialId, new ClassroomMaterialDTO.Update("T", "d")));

        assertEquals(HttpStatus.NOT_FOUND, ex.status());
    }

    @Test
    @DisplayName("Atualização com título inválido não salva")
    void updateInvalidTitle() {
        when(repository.findByIdAndClassroomId(materialId, classroomId)).thenReturn(Optional.of(material("a.pdf")));

        assertThrows(MaterialException.class, () -> service.update(EMAIL, classroomId, materialId,
                new ClassroomMaterialDTO.Update(" ", "d")));
        verify(repository, never()).save(any());
    }

    // replace

    @Test
    @DisplayName("Substituir arquivo salva e só então apaga o anterior após o commit")
    void replaceSwapsFileAndDeletesPrevious() {
        ClassroomMaterial existing = material("old.pdf");
        when(repository.findByIdAndClassroomId(materialId, classroomId)).thenReturn(Optional.of(existing));
        when(storage.store(file)).thenReturn(stored("new.docx"));
        when(repository.save(existing)).thenReturn(existing);

        ClassroomMaterial result = service.replace(EMAIL, classroomId, materialId, file);

        assertEquals("new.docx", result.getStoredFileName());
        assertEquals(42L, result.getFileSize());
        InOrder order = inOrder(repository, storage);
        order.verify(repository).save(existing);
        order.verify(storage).deleteAfterCommit(List.of("old.pdf"));
    }

    @Test
    @DisplayName("Substituir material inexistente não grava arquivo")
    void replaceMissingMaterial() {
        when(repository.findByIdAndClassroomId(materialId, classroomId)).thenReturn(Optional.empty());

        assertThrows(MaterialException.class, () -> service.replace(EMAIL, classroomId, materialId, file));
        verifyNoInteractions(storage);
    }

    // delete

    @Test
    @DisplayName("Excluir remove o registro e agenda a remoção do arquivo")
    void deleteRemovesRecordAndFile() {
        ClassroomMaterial existing = material("old.pdf");
        when(repository.findByIdAndClassroomId(materialId, classroomId)).thenReturn(Optional.of(existing));

        assertSame(existing, service.delete(EMAIL, classroomId, materialId));

        verify(repository).delete(existing);
        verify(storage).deleteAfterCommit(List.of("old.pdf"));
    }

    // download

    @Test
    @DisplayName("Download devolve recurso, nome, tipo, pré-visualização e instituição")
    void downloadReturnsResource() {
        Resource resource = new ByteArrayResource(new byte[] { 1 });
        when(repository.findByIdAndClassroomId(materialId, classroomId)).thenReturn(Optional.of(material("a.pdf")));
        when(storage.load("a.pdf")).thenReturn(resource);

        ClassroomMaterialService.Download download = service.download(EMAIL, classroomId, materialId);

        verify(classrooms).findById(EMAIL, classroomId);
        assertSame(resource, download.resource());
        assertEquals("aula.pdf", download.originalName());
        assertEquals("application/pdf", download.contentType());
        assertTrue(download.previewable());
        assertEquals(institution.getId(), download.institutionId());
    }

    @Test
    @DisplayName("Arquivo DOCX não é pré-visualizável")
    void downloadDocxNotPreviewable() {
        ClassroomMaterial doc = material("a.docx");
        doc.setOriginalFileName("a.docx");
        when(repository.findByIdAndClassroomId(materialId, classroomId)).thenReturn(Optional.of(doc));
        when(storage.load("a.docx")).thenReturn(new ByteArrayResource(new byte[0]));

        assertFalse(service.download(EMAIL, classroomId, materialId).previewable());
    }

    @Test
    @DisplayName("Download de material inexistente devolve 404")
    void downloadMissing() {
        when(repository.findByIdAndClassroomId(materialId, classroomId)).thenReturn(Optional.empty());

        assertThrows(MaterialException.class, () -> service.download(EMAIL, classroomId, materialId));
    }
}
