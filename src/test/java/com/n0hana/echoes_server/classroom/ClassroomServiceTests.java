package com.n0hana.echoes_server.classroom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.n0hana.echoes_server.classroom.exception.ClassroomArchivedException;
import com.n0hana.echoes_server.classroom.exception.ClassroomForbiddenException;
import com.n0hana.echoes_server.classroom.exception.ClassroomNotFoundException;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;

@ExtendWith(MockitoExtension.class)
class ClassroomServiceTests {

    private static final String EMAIL = "usuario@email.com";

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private ClassroomMemberRepository memberRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ClassroomService service;

    @Test
    @DisplayName("Professor cria a turma e vira moderador")
    void teacherCreatesClassroomAndBecomesModerator() {
        InstitutionModel institution = institution(true);
        Teacher teacher = teacher(institution);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(teacher));
        when(classroomRepository.save(any(ClassroomModel.class))).thenAnswer(i -> i.getArgument(0));

        ClassroomModel result = service.create(EMAIL, new ClassroomDTO.Request("Turma A", "Descrição"));

        assertEquals("Turma A", result.getTitle());
        assertEquals("Descrição", result.getDescription());
        assertSame(institution, result.getInstitution());
        assertFalse(result.isArchived());

        ArgumentCaptor<ClassroomMember> captor = ArgumentCaptor.forClass(ClassroomMember.class);
        verify(memberRepository).save(captor.capture());
        assertEquals(ClassroomMemberRole.MODERATOR, captor.getValue().getRole());
        assertSame(teacher, captor.getValue().getUser());
    }

    @Test
    @DisplayName("Não cria turma quando a instituição está inativa")
    void doesNotCreateWhenInstitutionIsInactive() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(teacher(institution(false))));

        assertThrows(ClassroomForbiddenException.class,
                () -> service.create(EMAIL, new ClassroomDTO.Request("Turma A", null)));

        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Não cria turma quando o usuário não é professor")
    void doesNotCreateWhenUserIsNotTeacher() {
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setInstitution(institution(true));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));

        assertThrows(ClassroomForbiddenException.class,
                () -> service.create(EMAIL, new ClassroomDTO.Request("Turma A", null)));

        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Gestor lista todas as turmas da instituição")
    void managerListsInstitutionClassrooms() {
        InstitutionModel institution = institution(true);
        Manager manager = new Manager();
        manager.setId(UUID.randomUUID());
        manager.setInstitution(institution);
        Pageable pageable = PageRequest.of(0, 10);
        Page<ClassroomModel> page = new PageImpl<>(List.of(classroom(institution, false)));

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(manager));
        when(classroomRepository.findByInstitutionId(institution.getId(), pageable)).thenReturn(page);

        assertSame(page, service.findAll(EMAIL, pageable));
    }

    @Test
    @DisplayName("Professor lista apenas as turmas em que é membro")
    void teacherListsOnlyHisClassrooms() {
        Teacher teacher = teacher(institution(true));
        Pageable pageable = PageRequest.of(0, 10);
        Page<ClassroomModel> page = new PageImpl<>(List.of(classroom(institution(true), false)));

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(teacher));
        when(classroomRepository.findByMemberId(teacher.getId(), pageable)).thenReturn(page);

        assertSame(page, service.findAll(EMAIL, pageable));
    }

    @Test
    @DisplayName("Membro consulta a turma pelo id")
    void memberFindsClassroomById() {
        Student student = student();
        ClassroomModel classroom = classroom(institution(true), false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), student.getId())).thenReturn(true);

        assertSame(classroom, service.findById(EMAIL, classroom.getId()));
    }

    @Test
    @DisplayName("Aluno que não é membro não acessa a turma")
    void nonMemberCannotFindClassroom() {
        Student student = student();
        ClassroomModel classroom = classroom(institution(true), false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), student.getId())).thenReturn(false);

        assertThrows(ClassroomForbiddenException.class, () -> service.findById(EMAIL, classroom.getId()));
    }

    @Test
    @DisplayName("Lança exceção ao buscar turma inexistente")
    void throwsWhenClassroomDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student()));
        when(classroomRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ClassroomNotFoundException.class, () -> service.findById(EMAIL, id));
    }

    @Test
    @DisplayName("Moderador altera título e descrição")
    void moderatorUpdatesClassroom() {
        Teacher teacher = teacher(institution(true));
        ClassroomModel classroom = classroom(institution(true), false);
        mockModerator(teacher, classroom, true);
        when(classroomRepository.save(classroom)).thenReturn(classroom);

        ClassroomModel result = service.update(EMAIL, classroom.getId(), new ClassroomDTO.Request("Novo", "Nova"));

        assertEquals("Novo", result.getTitle());
        assertEquals("Nova", result.getDescription());
    }

    @Test
    @DisplayName("Professor que não é moderador não altera a turma")
    void nonModeratorCannotUpdate() {
        Teacher teacher = teacher(institution(true));
        ClassroomModel classroom = classroom(institution(true), false);
        mockModerator(teacher, classroom, false);

        assertThrows(ClassroomForbiddenException.class,
                () -> service.update(EMAIL, classroom.getId(), new ClassroomDTO.Request("Novo", null)));

        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Turma arquivada não pode ser alterada")
    void archivedClassroomCannotBeUpdated() {
        Teacher teacher = teacher(institution(true));
        ClassroomModel classroom = classroom(institution(true), true);
        mockModerator(teacher, classroom, true);

        assertThrows(ClassroomArchivedException.class,
                () -> service.update(EMAIL, classroom.getId(), new ClassroomDTO.Request("Novo", null)));

        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Moderador arquiva a turma")
    void moderatorArchivesClassroom() {
        Teacher teacher = teacher(institution(true));
        ClassroomModel classroom = classroom(institution(true), false);
        mockModerator(teacher, classroom, true);
        when(classroomRepository.save(classroom)).thenReturn(classroom);

        service.archive(EMAIL, classroom.getId());

        assertTrue(classroom.isArchived());
    }

    @Test
    @DisplayName("Moderador reativa a turma arquivada")
    void moderatorUnarchivesClassroom() {
        Teacher teacher = teacher(institution(true));
        ClassroomModel classroom = classroom(institution(true), true);
        mockModerator(teacher, classroom, true);
        when(classroomRepository.save(classroom)).thenReturn(classroom);

        service.unarchive(EMAIL, classroom.getId());

        assertFalse(classroom.isArchived());
    }

    @Test
    @DisplayName("Quem não é moderador não arquiva a turma")
    void nonModeratorCannotArchive() {
        Teacher teacher = teacher(institution(true));
        ClassroomModel classroom = classroom(institution(true), false);
        mockModerator(teacher, classroom, false);

        assertThrows(ClassroomForbiddenException.class, () -> service.archive(EMAIL, classroom.getId()));

        assertFalse(classroom.isArchived());
    }

    private void mockModerator(Teacher teacher, ClassroomModel classroom, boolean moderator) {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(teacher));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(memberRepository.existsByClassroomIdAndUserIdAndRole(
                classroom.getId(), teacher.getId(), ClassroomMemberRole.MODERATOR)).thenReturn(moderator);
    }

    private InstitutionModel institution(boolean active) {
        return InstitutionModel.builder().id(UUID.randomUUID()).active(active).build();
    }

    private Teacher teacher(InstitutionModel institution) {
        Teacher teacher = new Teacher();
        teacher.setId(UUID.randomUUID());
        teacher.setEmail(EMAIL);
        teacher.setInstitution(institution);
        return teacher;
    }

    private Student student() {
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setEmail(EMAIL);
        student.setInstitution(institution(true));
        return student;
    }

    private ClassroomModel classroom(InstitutionModel institution, boolean archived) {
        return ClassroomModel.builder()
                .id(UUID.randomUUID())
                .title("Turma")
                .institution(institution)
                .archived(archived)
                .build();
    }
}
