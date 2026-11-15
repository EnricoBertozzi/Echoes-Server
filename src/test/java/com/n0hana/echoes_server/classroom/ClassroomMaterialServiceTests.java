package com.n0hana.echoes_server.classroom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

import com.n0hana.echoes_server.classroom.exception.ClassroomArchivedException;
import com.n0hana.echoes_server.classroom.exception.ClassroomForbiddenException;
import com.n0hana.echoes_server.classroom.exception.ClassroomNotFoundException;
import com.n0hana.echoes_server.infra.file.FileStorageService;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;

@ExtendWith(MockitoExtension.class)
class ClassroomMaterialServiceTests {

    private static final String EMAIL = "usuario@email.com";
    private static final Pageable PAGEABLE = PageRequest.of(0, 10);
    private static final String STORED_NAME = "uuid_Aula_1.pdf";

    @TempDir
    Path tempDir;

    @Mock
    private ClassroomMaterialRepository materialRepository;

    @Mock
    private ClassroomMemberRepository memberRepository;

    @Mock
    private ClassroomService classroomService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private ClassroomMaterialService service;

    // postar

    @Test
    @DisplayName("Professor da turma posta material")
    void teacherCreatesMaterial() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        Teacher teacher = teacher(institution);
        MockMultipartFile file = pdf();
        mockUser(teacher);
        mockClassroom(classroom);
        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), teacher.getId())).thenReturn(true);
        when(fileStorageService.store(eq(file), anyString(), eq(classroom.getId().toString()), any()))
                .thenReturn(STORED_NAME);
        when(materialRepository.save(any(ClassroomMaterial.class))).thenAnswer(i -> i.getArgument(0));

        ClassroomMaterial result = service.create(EMAIL, classroom.getId(),
                new ClassroomMaterialDTO.Request(" Aula 1 ", " Cardiologia "), file);

        assertEquals("Aula 1", result.getTitle());
        assertEquals("Cardiologia", result.getTopic());
        assertEquals(STORED_NAME, result.getFileName());
        assertSame(teacher, result.getAuthor());
        assertSame(classroom, result.getClassroom());
        verify(fileStorageService).store(eq(file), eq("Aula_1"), eq(classroom.getId().toString()), any());
    }

    @Test
    @DisplayName("Gestor posta material em qualquer turma da instituição")
    void managerCreatesMaterial() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        Manager manager = manager(institution);
        MockMultipartFile file = pdf();
        mockUser(manager);
        mockClassroom(classroom);
        when(fileStorageService.store(eq(file), anyString(), eq(classroom.getId().toString()), any()))
                .thenReturn(STORED_NAME);
        when(materialRepository.save(any(ClassroomMaterial.class))).thenAnswer(i -> i.getArgument(0));

        ClassroomMaterial result = service.create(EMAIL, classroom.getId(),
                new ClassroomMaterialDTO.Request("Aula 1", "Cardiologia"), file);

        assertSame(manager, result.getAuthor());
    }

    @Test
    @DisplayName("Aluno não posta material")
    void studentCannotCreate() {
        ClassroomModel classroom = classroom(institution(), false);
        mockUser(student(institution()));
        mockClassroom(classroom);

        assertThrows(ClassroomForbiddenException.class, () -> service.create(EMAIL, classroom.getId(),
                new ClassroomMaterialDTO.Request("Aula 1", "Cardiologia"), pdf()));

        verifyNoInteractions(fileStorageService);
        verify(materialRepository, never()).save(any());
    }

    @Test
    @DisplayName("Professor que não é da turma não posta material")
    void teacherOutsideClassroomCannotCreate() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        Teacher teacher = teacher(institution);
        mockUser(teacher);
        mockClassroom(classroom);
        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), teacher.getId())).thenReturn(false);

        assertThrows(ClassroomForbiddenException.class, () -> service.create(EMAIL, classroom.getId(),
                new ClassroomMaterialDTO.Request("Aula 1", "Cardiologia"), pdf()));

        verifyNoInteractions(fileStorageService);
    }

    @Test
    @DisplayName("Gestor de outra instituição não posta material")
    void managerOfOtherInstitutionCannotCreate() {
        ClassroomModel classroom = classroom(institution(), false);
        mockUser(manager(institution()));
        mockClassroom(classroom);

        assertThrows(ClassroomForbiddenException.class, () -> service.create(EMAIL, classroom.getId(),
                new ClassroomMaterialDTO.Request("Aula 1", "Cardiologia"), pdf()));

        verifyNoInteractions(fileStorageService);
    }

    @Test
    @DisplayName("Turma arquivada não aceita postagem")
    void archivedClassroomRejectsCreate() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, true);
        mockUser(manager(institution));
        mockClassroom(classroom);

        assertThrows(ClassroomArchivedException.class, () -> service.create(EMAIL, classroom.getId(),
                new ClassroomMaterialDTO.Request("Aula 1", "Cardiologia"), pdf()));

        verifyNoInteractions(fileStorageService);
    }

    // listar e baixar

    @Test
    @DisplayName("Membro da turma lista o material com filtro de título e tópico")
    void listsMaterials() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        Teacher author = teacher(institution);
        ClassroomMaterial material = material(classroom, author);
        mockClassroom(classroom);
        when(materialRepository.findByClassroomIdAndTitleContainingIgnoreCaseAndTopicContainingIgnoreCase(
                classroom.getId(), "aula", "cardio", PAGEABLE)).thenReturn(new PageImpl<>(List.of(material)));

        Page<ClassroomMaterialDTO.Info> result = service.findAll(EMAIL, classroom.getId(), "aula", "cardio",
                PAGEABLE);

        assertEquals(1, result.getTotalElements());
        assertEquals("Aula 1", result.getContent().get(0).title());
        assertEquals("Cardiologia", result.getContent().get(0).topic());
        assertEquals(author.getId(), result.getContent().get(0).authorId());
        assertEquals(classroom.getId(), result.getContent().get(0).classroomId());
    }

    @Test
    @DisplayName("Quem não enxerga a turma não lista o material")
    void nonMemberCannotList() {
        UUID classroomId = UUID.randomUUID();
        when(classroomService.findById(EMAIL, classroomId))
                .thenThrow(new ClassroomForbiddenException("Sem acesso a esta turma"));

        assertThrows(ClassroomForbiddenException.class,
                () -> service.findAll(EMAIL, classroomId, "", "", PAGEABLE));

        verifyNoInteractions(materialRepository);
    }

    @Test
    @DisplayName("Baixa o material com o título como nome do arquivo")
    void downloadsMaterial() throws Exception {
        ClassroomModel classroom = classroom(institution(), false);
        ClassroomMaterial material = material(classroom, teacher(institution()));
        Path file = Files.writeString(tempDir.resolve("arquivo.pdf"), "conteudo");
        mockClassroom(classroom);
        when(materialRepository.findByIdAndClassroomId(material.getId(), classroom.getId()))
                .thenReturn(Optional.of(material));
        when(fileStorageService.load(classroom.getId().toString(), STORED_NAME)).thenReturn(file);

        ClassroomMaterialDTO.Download result = service.download(EMAIL, classroom.getId(), material.getId());

        assertEquals(file, result.path());
        assertEquals("Aula 1.pdf", result.name());
    }

    @Test
    @DisplayName("Download falha quando o arquivo não existe no disco")
    void downloadFailsWhenFileIsMissing() {
        ClassroomModel classroom = classroom(institution(), false);
        ClassroomMaterial material = material(classroom, teacher(institution()));
        mockClassroom(classroom);
        when(materialRepository.findByIdAndClassroomId(material.getId(), classroom.getId()))
                .thenReturn(Optional.of(material));
        when(fileStorageService.load(classroom.getId().toString(), STORED_NAME))
                .thenReturn(tempDir.resolve("nao-existe.pdf"));

        assertThrows(ClassroomNotFoundException.class,
                () -> service.download(EMAIL, classroom.getId(), material.getId()));
    }

    @Test
    @DisplayName("Download falha quando o material não é da turma")
    void downloadFailsWhenMaterialNotFound() {
        ClassroomModel classroom = classroom(institution(), false);
        UUID materialId = UUID.randomUUID();
        mockClassroom(classroom);
        when(materialRepository.findByIdAndClassroomId(materialId, classroom.getId())).thenReturn(Optional.empty());

        assertThrows(ClassroomNotFoundException.class,
                () -> service.download(EMAIL, classroom.getId(), materialId));
    }

    // editar

    @Test
    @DisplayName("Professor edita a própria postagem")
    void teacherUpdatesOwnMaterial() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        Teacher teacher = teacher(institution);
        ClassroomMaterial material = material(classroom, teacher);
        mockUser(teacher);
        mockClassroom(classroom);
        mockMaterial(classroom, material);
        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), teacher.getId())).thenReturn(true);
        when(materialRepository.save(any(ClassroomMaterial.class))).thenAnswer(i -> i.getArgument(0));

        ClassroomMaterial result = service.update(EMAIL, classroom.getId(), material.getId(),
                new ClassroomMaterialDTO.Request("Aula 2", "Prova 1"));

        assertEquals("Aula 2", result.getTitle());
        assertEquals("Prova 1", result.getTopic());
    }

    @Test
    @DisplayName("Gestor edita a postagem de qualquer professor da instituição")
    void managerUpdatesAnyMaterial() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        ClassroomMaterial material = material(classroom, teacher(institution));
        mockUser(manager(institution));
        mockClassroom(classroom);
        mockMaterial(classroom, material);
        when(materialRepository.save(any(ClassroomMaterial.class))).thenAnswer(i -> i.getArgument(0));

        ClassroomMaterial result = service.update(EMAIL, classroom.getId(), material.getId(),
                new ClassroomMaterialDTO.Request("Aula 2", "Prova 1"));

        assertEquals("Aula 2", result.getTitle());
    }

    @Test
    @DisplayName("Professor não edita a postagem de outro professor")
    void teacherCannotUpdateOthersMaterial() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        ClassroomMaterial material = material(classroom, teacher(institution));
        mockUser(teacher(institution));
        mockClassroom(classroom);
        mockMaterial(classroom, material);

        assertThrows(ClassroomForbiddenException.class, () -> service.update(EMAIL, classroom.getId(),
                material.getId(), new ClassroomMaterialDTO.Request("Aula 2", "Prova 1")));

        verify(materialRepository, never()).save(any());
    }

    @Test
    @DisplayName("Professor removido da turma não edita a própria postagem")
    void removedTeacherCannotUpdateOwnMaterial() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        Teacher teacher = teacher(institution);
        ClassroomMaterial material = material(classroom, teacher);
        mockUser(teacher);
        mockClassroom(classroom);
        mockMaterial(classroom, material);
        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), teacher.getId())).thenReturn(false);

        assertThrows(ClassroomForbiddenException.class, () -> service.update(EMAIL, classroom.getId(),
                material.getId(), new ClassroomMaterialDTO.Request("Aula 2", "Prova 1")));
    }

    @Test
    @DisplayName("Aluno não edita postagem")
    void studentCannotUpdate() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        ClassroomMaterial material = material(classroom, teacher(institution));
        mockUser(student(institution));
        mockClassroom(classroom);
        mockMaterial(classroom, material);

        assertThrows(ClassroomForbiddenException.class, () -> service.update(EMAIL, classroom.getId(),
                material.getId(), new ClassroomMaterialDTO.Request("Aula 2", "Prova 1")));
    }

    @Test
    @DisplayName("Turma arquivada não aceita edição")
    void archivedClassroomRejectsUpdate() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, true);
        ClassroomMaterial material = material(classroom, teacher(institution));
        mockUser(manager(institution));
        mockClassroom(classroom);
        mockMaterial(classroom, material);

        assertThrows(ClassroomArchivedException.class, () -> service.update(EMAIL, classroom.getId(),
                material.getId(), new ClassroomMaterialDTO.Request("Aula 2", "Prova 1")));

        verify(materialRepository, never()).save(any());
    }

    // excluir

    @Test
    @DisplayName("Professor exclui a própria postagem e o arquivo")
    void teacherDeletesOwnMaterial() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        Teacher teacher = teacher(institution);
        ClassroomMaterial material = material(classroom, teacher);
        mockUser(teacher);
        mockClassroom(classroom);
        mockMaterial(classroom, material);
        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), teacher.getId())).thenReturn(true);

        ClassroomMaterial result = service.delete(EMAIL, classroom.getId(), material.getId());

        assertSame(material, result);
        verify(materialRepository).delete(material);
        verify(fileStorageService).delete(classroom.getId().toString(), STORED_NAME);
    }

    @Test
    @DisplayName("Gestor exclui a postagem de qualquer professor, mesmo com a turma arquivada")
    void managerDeletesAnyMaterialEvenArchived() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, true);
        ClassroomMaterial material = material(classroom, teacher(institution));
        mockUser(manager(institution));
        mockClassroom(classroom);
        mockMaterial(classroom, material);

        service.delete(EMAIL, classroom.getId(), material.getId());

        verify(materialRepository).delete(material);
        verify(fileStorageService).delete(classroom.getId().toString(), STORED_NAME);
    }

    @Test
    @DisplayName("Professor não exclui a postagem de outro professor")
    void teacherCannotDeleteOthersMaterial() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        ClassroomMaterial material = material(classroom, teacher(institution));
        mockUser(teacher(institution));
        mockClassroom(classroom);
        mockMaterial(classroom, material);

        assertThrows(ClassroomForbiddenException.class,
                () -> service.delete(EMAIL, classroom.getId(), material.getId()));

        verify(materialRepository, never()).delete(any(ClassroomMaterial.class));
        verifyNoInteractions(fileStorageService);
    }

    @Test
    @DisplayName("Aluno não exclui postagem")
    void studentCannotDelete() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        ClassroomMaterial material = material(classroom, teacher(institution));
        mockUser(student(institution));
        mockClassroom(classroom);
        mockMaterial(classroom, material);

        assertThrows(ClassroomForbiddenException.class,
                () -> service.delete(EMAIL, classroom.getId(), material.getId()));

        verifyNoInteractions(fileStorageService);
    }

    // apoio

    private void mockUser(User user) {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    private void mockClassroom(ClassroomModel classroom) {
        when(classroomService.findById(EMAIL, classroom.getId())).thenReturn(classroom);
    }

    private void mockMaterial(ClassroomModel classroom, ClassroomMaterial material) {
        when(materialRepository.findByIdAndClassroomId(material.getId(), classroom.getId()))
                .thenReturn(Optional.of(material));
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "aula.pdf", "application/pdf", "conteudo".getBytes());
    }

    private InstitutionModel institution() {
        return InstitutionModel.builder().id(UUID.randomUUID()).active(true).build();
    }

    private Manager manager(InstitutionModel institution) {
        Manager manager = new Manager();
        manager.setId(UUID.randomUUID());
        manager.setEmail(EMAIL);
        manager.setInstitution(institution);
        return manager;
    }

    private Teacher teacher(InstitutionModel institution) {
        Teacher teacher = new Teacher();
        teacher.setId(UUID.randomUUID());
        teacher.setEmail(EMAIL);
        teacher.setInstitution(institution);
        return teacher;
    }

    private Student student(InstitutionModel institution) {
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setEmail(EMAIL);
        student.setInstitution(institution);
        return student;
    }

    private ClassroomModel classroom(InstitutionModel institution, boolean archived) {
        return ClassroomModel.builder()
                .id(UUID.randomUUID())
                .title("Turma")
                .period("2026/01")
                .institution(institution)
                .archived(archived)
                .build();
    }

    private ClassroomMaterial material(ClassroomModel classroom, User author) {
        return ClassroomMaterial.builder()
                .id(UUID.randomUUID())
                .classroom(classroom)
                .author(author)
                .title("Aula 1")
                .topic("Cardiologia")
                .fileName(STORED_NAME)
                .build();
    }
}
