package com.n0hana.echoes_server.classroom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.n0hana.echoes_server.classroom.exception.ClassroomArchivedException;
import com.n0hana.echoes_server.classroom.exception.ClassroomCodeActiveException;
import com.n0hana.echoes_server.classroom.exception.ClassroomForbiddenException;
import com.n0hana.echoes_server.classroom.exception.ClassroomNotFoundException;
import com.n0hana.echoes_server.classroom.exception.ClassroomStudentEnrolledException;
import com.n0hana.echoes_server.classroom.material.ClassroomMaterialRepository;
import com.n0hana.echoes_server.classroom.material.ClassroomMaterialStorage;
import com.n0hana.echoes_server.infra.notifier.NotifierService;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;
import com.n0hana.echoes_server.user.model.UserRole;

@ExtendWith(MockitoExtension.class)
class ClassroomServiceTests {

    private static final String EMAIL = "usuario@email.com";
    private static final String TEACHER_EMAIL = "professor@email.com";
    private static final String CODE = "ABC234";
    private static final Pageable PAGEABLE = PageRequest.of(0, 10);

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private com.n0hana.echoes_server.auth.attempt.AttemptService attempts;

    @Mock
    private ClassroomMemberRepository memberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClassroomCodeService codeService;

    @Mock
    private NotifierService notifierService;

    @Mock private ClassroomMaterialRepository materialRepository;
    @Mock private ClassroomMaterialStorage materialStorage;
    @Mock private com.n0hana.echoes_server.infra.javers.checkpoint.JaversCheckpointService checkpoints;

    @InjectMocks
    private ClassroomService service;

    // criar

    @Test
    @DisplayName("Gestor cria a turma na própria instituição")
    void managerCreatesClassroom() {
        InstitutionModel institution = institution(true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(manager(institution)));
        when(classroomRepository.save(any(ClassroomModel.class))).thenAnswer(i -> i.getArgument(0));

        ClassroomModel result = service.create(EMAIL, new ClassroomDTO.Request("Turma A", "Descrição", "2026/01"));

        assertEquals("Turma A", result.getTitle());
        assertEquals("Descrição", result.getDescription());
        assertEquals("2026/01", result.getPeriod());
        assertSame(institution, result.getInstitution());
        assertFalse(result.isArchived());
        verifyNoInteractions(memberRepository, codeService);
    }

    @Test
    @DisplayName("Não cria turma quando a instituição está inativa")
    void doesNotCreateWhenInstitutionIsInactive() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(manager(institution(false))));

        assertThrows(ClassroomForbiddenException.class,
                () -> service.create(EMAIL, new ClassroomDTO.Request("Turma A", null, "2026/01")));

        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Professor não cria turma")
    void teacherCannotCreate() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(teacher(institution(true))));

        assertThrows(ClassroomForbiddenException.class,
                () -> service.create(EMAIL, new ClassroomDTO.Request("Turma A", null, "2026/01")));

        verify(classroomRepository, never()).save(any());
    }

    // listar e consultar

    @Test
    @DisplayName("Admin lista todas as turmas com busca por nome")
    void adminListsAllClassrooms() {
        Page<ClassroomModel> page = new PageImpl<>(List.of(classroom(institution(true), false)));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(admin()));
        when(classroomRepository.findByTitleContainingIgnoreCase("turma", PAGEABLE)).thenReturn(page);

        assertSame(page, service.findAll(EMAIL, "turma", PAGEABLE));
    }

    @Test
    @DisplayName("Gestor lista todas as turmas da instituição")
    void managerListsInstitutionClassrooms() {
        InstitutionModel institution = institution(true);
        Page<ClassroomModel> page = new PageImpl<>(List.of(classroom(institution, false)));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(manager(institution)));
        when(classroomRepository.findByInstitutionIdAndTitleContainingIgnoreCase(institution.getId(), "", PAGEABLE))
                .thenReturn(page);

        assertSame(page, service.findAll(EMAIL, "", PAGEABLE));
    }

    @Test
    @DisplayName("Professor lista apenas as turmas em que participa")
    void teacherListsOnlyHisClassrooms() {
        Teacher teacher = teacher(institution(true));
        Page<ClassroomModel> page = new PageImpl<>(List.of(classroom(institution(true), false)));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(teacher));
        when(classroomRepository.findByMemberId(teacher.getId(), "", PAGEABLE)).thenReturn(page);

        assertSame(page, service.findAll(EMAIL, "", PAGEABLE));
    }

    @Test
    @DisplayName("Membro consulta a turma pelo id")
    void memberFindsClassroomById() {
        Student student = student(institution(true));
        ClassroomModel classroom = classroom(institution(true), false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), student.getId())).thenReturn(true);

        assertSame(classroom, service.findById(EMAIL, classroom.getId()));
    }

    @Test
    @DisplayName("Gestor consulta turma da própria instituição sem ser membro")
    void managerFindsClassroomOfHisInstitution() {
        InstitutionModel institution = institution(true);
        ClassroomModel classroom = classroom(institution, false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(manager(institution)));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));

        assertSame(classroom, service.findById(EMAIL, classroom.getId()));
    }

    @Test
    @DisplayName("Gestor de outra instituição não acessa a turma")
    void managerOfOtherInstitutionCannotFindClassroom() {
        ClassroomModel classroom = classroom(institution(true), false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(manager(institution(true))));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));

        assertThrows(ClassroomForbiddenException.class, () -> service.findById(EMAIL, classroom.getId()));
    }

    @Test
    @DisplayName("Admin consulta qualquer turma")
    void adminFindsAnyClassroom() {
        ClassroomModel classroom = classroom(institution(true), false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(admin()));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));

        assertSame(classroom, service.findById(EMAIL, classroom.getId()));
    }

    @Test
    @DisplayName("Aluno que não é membro não acessa a turma")
    void nonMemberCannotFindClassroom() {
        Student student = student(institution(true));
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
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student(institution(true))));
        when(classroomRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ClassroomNotFoundException.class, () -> service.findById(EMAIL, id));
    }

    @Test
    @DisplayName("Professor da turma enxerga o código de entrada ativo")
    void teacherSeesJoinCode() {
        Teacher teacher = teacher(institution(true));
        UUID id = UUID.randomUUID();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(teacher));
        when(memberRepository.existsByClassroomIdAndUserId(id, teacher.getId())).thenReturn(true);
        when(codeService.findCode(id)).thenReturn(Optional.of(CODE));

        assertEquals(CODE, service.findJoinCode(EMAIL, id));
    }

    @Test
    @DisplayName("Professor de fora da turma não enxerga o código")
    void teacherOutsideClassroomDoesNotSeeJoinCode() {
        Teacher teacher = teacher(institution(true));
        UUID id = UUID.randomUUID();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(teacher));
        when(memberRepository.existsByClassroomIdAndUserId(id, teacher.getId())).thenReturn(false);

        assertNull(service.findJoinCode(EMAIL, id));
        verifyNoInteractions(codeService);
    }

    @Test
    @DisplayName("Aluno não enxerga o código de entrada")
    void studentDoesNotSeeJoinCode() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student(institution(true))));

        assertNull(service.findJoinCode(EMAIL, UUID.randomUUID()));
        verifyNoInteractions(codeService);
    }

    @Test
    @DisplayName("Membro lista os integrantes da turma")
    void memberListsClassroomMembers() {
        Student student = student(institution(true));
        ClassroomModel classroom = classroom(institution(true), false);
        ClassroomMember member = member(classroom, student);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), student.getId())).thenReturn(true);
        when(memberRepository.findByClassroomId(classroom.getId(), PAGEABLE))
                .thenReturn(new PageImpl<>(List.of(member)));

        Page<ClassroomDTO.MemberInfo> result = service.findMembers(EMAIL, classroom.getId(), PAGEABLE);

        assertEquals(1, result.getTotalElements());
        assertEquals(UserRole.STUDENT, result.getContent().get(0).role());
    }

    @Test
    @DisplayName("Quem não vê a turma não lista os integrantes")
    void nonMemberCannotListMembers() {
        Student student = student(institution(true));
        ClassroomModel classroom = classroom(institution(true), false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), student.getId())).thenReturn(false);

        assertThrows(ClassroomForbiddenException.class,
                () -> service.findMembers(EMAIL, classroom.getId(), PAGEABLE));
    }

    // alterar, arquivar e excluir

    @Test
    @DisplayName("Gestor altera título, descrição e período")
    void managerUpdatesClassroom() {
        InstitutionModel institution = institution(true);
        ClassroomModel classroom = classroom(institution, false);
        mockManager(manager(institution), classroom);
        when(classroomRepository.save(classroom)).thenReturn(classroom);

        ClassroomModel result = service.update(EMAIL, classroom.getId(),
                new ClassroomDTO.Request("Novo", "Nova", "2026/02"));

        assertEquals("Novo", result.getTitle());
        assertEquals("Nova", result.getDescription());
        assertEquals("2026/02", result.getPeriod());
    }

    @Test
    @DisplayName("Gestor de outra instituição não altera a turma")
    void managerOfOtherInstitutionCannotUpdate() {
        ClassroomModel classroom = classroom(institution(true), false);
        mockManager(manager(institution(true)), classroom);

        assertThrows(ClassroomForbiddenException.class,
                () -> service.update(EMAIL, classroom.getId(), new ClassroomDTO.Request("Novo", null, "2026/01")));

        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Professor não altera a turma")
    void teacherCannotUpdate() {
        InstitutionModel institution = institution(true);
        ClassroomModel classroom = classroom(institution, false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(teacher(institution)));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));

        assertThrows(ClassroomForbiddenException.class,
                () -> service.update(EMAIL, classroom.getId(), new ClassroomDTO.Request("Novo", null, "2026/01")));

        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Turma arquivada não pode ser alterada")
    void archivedClassroomCannotBeUpdated() {
        InstitutionModel institution = institution(true);
        ClassroomModel classroom = classroom(institution, true);
        mockManager(manager(institution), classroom);

        assertThrows(ClassroomArchivedException.class,
                () -> service.update(EMAIL, classroom.getId(), new ClassroomDTO.Request("Novo", null, "2026/01")));

        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Gestor arquiva a turma")
    void managerArchivesClassroom() {
        InstitutionModel institution = institution(true);
        ClassroomModel classroom = classroom(institution, false);
        mockManager(manager(institution), classroom);
        when(classroomRepository.save(classroom)).thenReturn(classroom);

        service.archive(EMAIL, classroom.getId());

        assertTrue(classroom.isArchived());
    }

    @Test
    @DisplayName("Gestor reativa a turma arquivada")
    void managerUnarchivesClassroom() {
        InstitutionModel institution = institution(true);
        ClassroomModel classroom = classroom(institution, true);
        mockManager(manager(institution), classroom);
        when(classroomRepository.save(classroom)).thenReturn(classroom);

        service.unarchive(EMAIL, classroom.getId());

        assertFalse(classroom.isArchived());
    }

    @Test
    @DisplayName("Gestor exclui a turma e os vínculos dos membros")
    void managerDeletesClassroom() {
        InstitutionModel institution = institution(true);
        ClassroomModel classroom = classroom(institution, false);
        mockManager(manager(institution), classroom);

        service.delete(EMAIL, classroom.getId());

        verify(memberRepository).deleteByClassroomId(classroom.getId());
        // exclusão lógica: a turma é marcada e salva, nunca apagada do banco
        assertTrue(classroom.isDeleted());
        verify(classroomRepository).save(classroom);
        verify(classroomRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Gestor de outra instituição não exclui a turma")
    void managerOfOtherInstitutionCannotDelete() {
        ClassroomModel classroom = classroom(institution(true), false);
        mockManager(manager(institution(true)), classroom);

        assertThrows(ClassroomForbiddenException.class, () -> service.delete(EMAIL, classroom.getId()));

        verify(classroomRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Gestor limpa só os alunos da turma")
    void managerClearsOnlyStudents() {
        InstitutionModel institution = institution(true);
        ClassroomModel classroom = classroom(institution, false);
        ClassroomMember studentMember = member(classroom, student(institution));
        ClassroomMember teacherMember = member(classroom, teacher(institution));
        mockManager(manager(institution), classroom);
        when(memberRepository.findByClassroomId(classroom.getId()))
                .thenReturn(List.of(studentMember, teacherMember));

        service.clearStudents(EMAIL, classroom.getId());

        verify(memberRepository).deleteAll(List.of(studentMember));
    }

    @Test
    @DisplayName("Limpar mais alunos que o limite cria o checkpoint antes de apagar; abaixo do limite, não")
    void clearStudentsCreatesCheckpointOnlyAboveThreshold() {
        InstitutionModel institution = institution(true);
        ClassroomModel classroom = classroom(institution, false);
        ClassroomMember studentMember = member(classroom, student(institution));
        mockManager(manager(institution), classroom);
        when(memberRepository.findByClassroomId(classroom.getId())).thenReturn(List.of(studentMember));

        service.clearStudents(EMAIL, classroom.getId());
        verify(checkpoints, never()).create(any(), any(), any());

        org.springframework.test.util.ReflectionTestUtils.setField(service, "checkpointThreshold", 0);
        service.clearStudents(EMAIL, classroom.getId());

        var order = org.mockito.Mockito.inOrder(checkpoints, memberRepository);
        order.verify(checkpoints).create(org.mockito.ArgumentMatchers.eq(ClassroomMember.class.getName()), any(), org.mockito.ArgumentMatchers.eq(EMAIL));
        order.verify(memberRepository, org.mockito.Mockito.atLeastOnce()).deleteAll(List.of(studentMember));
    }

    @Test
    @DisplayName("Quem não é gestor da instituição não limpa a turma")
    void nonManagerCannotClearStudents() {
        InstitutionModel institution = institution(true);
        ClassroomModel classroom = classroom(institution, false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(teacher(institution)));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));

        assertThrows(ClassroomForbiddenException.class, () -> service.clearStudents(EMAIL, classroom.getId()));

        verify(memberRepository, never()).deleteAll(any());
    }

    // código de entrada

    @Test
    @DisplayName("Turma arquivada não gera código de entrada")
    void archivedClassroomDoesNotGenerateCode() {
        Teacher teacher = teacher(institution(true));
        ClassroomModel classroom = classroom(institution(true), true);
        mockTeacherMember(teacher, classroom, true);

        assertThrows(ClassroomArchivedException.class, () -> service.generateCode(EMAIL, classroom.getId()));

        verify(codeService, never()).generate(any());
    }

    @Test
    @DisplayName("Professor da turma gera novo código quando o anterior expirou")
    void teacherGeneratesNewCode() {
        Teacher teacher = teacher(institution(true));
        ClassroomModel classroom = classroom(institution(true), false);
        mockTeacherMember(teacher, classroom, true);
        when(codeService.findCode(classroom.getId())).thenReturn(Optional.empty());
        when(codeService.generate(classroom.getId())).thenReturn(CODE);
        when(codeService.secondsLeft(CODE)).thenReturn(900L);

        ClassroomDTO.CodeResponse response = service.generateCode(EMAIL, classroom.getId());

        assertEquals(CODE, response.code());
        assertEquals(900L, response.expiresInSeconds());
    }

    @Test
    @DisplayName("Não gera novo código enquanto o anterior está ativo")
    void doesNotGenerateWhileCodeIsActive() {
        Teacher teacher = teacher(institution(true));
        ClassroomModel classroom = classroom(institution(true), false);
        mockTeacherMember(teacher, classroom, true);
        when(codeService.findCode(classroom.getId())).thenReturn(Optional.of(CODE));

        assertThrows(ClassroomCodeActiveException.class, () -> service.generateCode(EMAIL, classroom.getId()));

        verify(codeService, never()).generate(any());
    }

    @Test
    @DisplayName("Professor de fora da turma não gera código")
    void teacherOutsideClassroomCannotGenerateCode() {
        Teacher teacher = teacher(institution(true));
        ClassroomModel classroom = classroom(institution(true), false);
        mockTeacherMember(teacher, classroom, false);

        assertThrows(ClassroomForbiddenException.class, () -> service.generateCode(EMAIL, classroom.getId()));

        verifyNoInteractions(codeService);
    }

    @Test
    @DisplayName("Gestor não gera código de entrada")
    void managerCannotGenerateCode() {
        InstitutionModel institution = institution(true);
        ClassroomModel classroom = classroom(institution, false);
        mockManager(manager(institution), classroom);

        assertThrows(ClassroomForbiddenException.class, () -> service.generateCode(EMAIL, classroom.getId()));

        verifyNoInteractions(codeService);
    }

    // entrar e sair

    @Test
    @DisplayName("Aluno da mesma instituição entra na turma pelo código em minúsculo")
    void studentJoinsClassroomWithCode() {
        InstitutionModel institution = institution(true);
        Student student = student(institution);
        ClassroomModel classroom = classroom(institution, false);
        mockJoin(student, classroom);

        ClassroomModel result = service.join(EMAIL, new ClassroomDTO.JoinRequest("abc234"));

        assertSame(classroom, result);
        ArgumentCaptor<ClassroomMember> captor = ArgumentCaptor.forClass(ClassroomMember.class);
        verify(memberRepository).save(captor.capture());
        assertSame(student, captor.getValue().getUser());
        assertSame(classroom, captor.getValue().getClassroom());
        assertEquals(student.getId(), captor.getValue().getStudentId());
    }

    @Test
    @DisplayName("Código inexistente ou expirado é recusado")
    void joinFailsWhenCodeDoesNotExist() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student(institution(true))));
        when(codeService.findClassroomId(CODE)).thenReturn(Optional.empty());

        assertThrows(ClassroomForbiddenException.class,
                () -> service.join(EMAIL, new ClassroomDTO.JoinRequest(CODE)));

        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Aluno de outra instituição não entra na turma")
    void studentOfOtherInstitutionCannotJoin() {
        Student student = student(institution(true));
        ClassroomModel classroom = classroom(institution(true), false);
        mockJoin(student, classroom);

        assertThrows(ClassroomForbiddenException.class,
                () -> service.join(EMAIL, new ClassroomDTO.JoinRequest(CODE)));

        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Professor não entra na turma pelo código")
    void teacherCannotJoin() {
        InstitutionModel institution = institution(true);
        ClassroomModel classroom = classroom(institution, false);
        mockJoin(teacher(institution), classroom);

        assertThrows(ClassroomForbiddenException.class,
                () -> service.join(EMAIL, new ClassroomDTO.JoinRequest(CODE)));

        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Aluno que já está em outra turma não entra")
    void studentAlreadyEnrolledCannotJoin() {
        InstitutionModel institution = institution(true);
        Student student = student(institution);
        ClassroomModel classroom = classroom(institution, false);
        mockJoin(student, classroom);
        when(memberRepository.existsByUserId(student.getId())).thenReturn(true);

        assertThrows(ClassroomStudentEnrolledException.class,
                () -> service.join(EMAIL, new ClassroomDTO.JoinRequest(CODE)));

        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Turma arquivada não aceita novos alunos")
    void archivedClassroomDoesNotAcceptStudents() {
        InstitutionModel institution = institution(true);
        Student student = student(institution);
        ClassroomModel classroom = classroom(institution, true);
        mockJoin(student, classroom);

        assertThrows(ClassroomArchivedException.class,
                () -> service.join(EMAIL, new ClassroomDTO.JoinRequest(CODE)));

        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Aluno que já é membro da turma recebe a turma sem erro")
    void joinIsIdempotentForMembers() {
        InstitutionModel institution = institution(true);
        Student student = student(institution);
        ClassroomModel classroom = classroom(institution, false);
        mockJoin(student, classroom);
        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), student.getId())).thenReturn(true);

        assertSame(classroom, service.join(EMAIL, new ClassroomDTO.JoinRequest(CODE)));

        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Professor sai da turma")
    void teacherLeavesClassroom() {
        Teacher teacher = teacher(institution(true));
        ClassroomModel classroom = classroom(institution(true), false);
        ClassroomMember member = member(classroom, teacher);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(teacher));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(memberRepository.findByClassroomIdAndUserId(classroom.getId(), teacher.getId()))
                .thenReturn(Optional.of(member));

        service.leave(EMAIL, classroom.getId());

        verify(memberRepository).delete(member);
    }

    @Test
    @DisplayName("Aluno sai da turma")
    void studentLeavesClassroom() {
        Student student = student(institution(true));
        ClassroomModel classroom = classroom(institution(true), false);
        ClassroomMember member = member(classroom, student);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(memberRepository.findByClassroomIdAndUserId(classroom.getId(), student.getId()))
                .thenReturn(Optional.of(member));

        service.leave(EMAIL, classroom.getId());

        verify(memberRepository).delete(member);
    }

    @Test
    @DisplayName("Quem não é membro não consegue sair da turma")
    void nonMemberCannotLeave() {
        Student student = student(institution(true));
        ClassroomModel classroom = classroom(institution(true), false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(memberRepository.findByClassroomIdAndUserId(classroom.getId(), student.getId()))
                .thenReturn(Optional.empty());

        assertThrows(ClassroomForbiddenException.class, () -> service.leave(EMAIL, classroom.getId()));

        verify(memberRepository, never()).delete(any());
    }

    // remover aluno

    @Test
    @DisplayName("Gestor remove aluno da turma da instituição")
    void managerRemovesStudent() {
        InstitutionModel institution = institution(true);
        Student student = student(institution);
        ClassroomModel classroom = classroom(institution, false);
        ClassroomMember member = member(classroom, student);
        mockManager(manager(institution), classroom);
        when(memberRepository.findByClassroomIdAndUserId(classroom.getId(), student.getId()))
                .thenReturn(Optional.of(member));

        service.removeMember(EMAIL, classroom.getId(), student.getId());

        verify(memberRepository).delete(member);
    }

    @Test
    @DisplayName("Gestor remove professor da turma da instituição")
    void managerRemovesTeacher() {
        InstitutionModel institution = institution(true);
        Teacher teacher = teacher(institution);
        ClassroomModel classroom = classroom(institution, false);
        ClassroomMember member = member(classroom, teacher);
        mockManager(manager(institution), classroom);
        when(memberRepository.findByClassroomIdAndUserId(classroom.getId(), teacher.getId()))
                .thenReturn(Optional.of(member));

        service.removeMember(EMAIL, classroom.getId(), teacher.getId());

        verify(memberRepository).delete(member);
    }

    @Test
    @DisplayName("Gestor de outra instituição não remove aluno")
    void managerOfOtherInstitutionCannotRemoveStudent() {
        ClassroomModel classroom = classroom(institution(true), false);
        mockManager(manager(institution(true)), classroom);

        assertThrows(ClassroomForbiddenException.class,
                () -> service.removeMember(EMAIL, classroom.getId(), UUID.randomUUID()));

        verify(memberRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Lança exceção ao remover quem não é membro da turma")
    void throwsWhenMemberDoesNotExist() {
        InstitutionModel institution = institution(true);
        ClassroomModel classroom = classroom(institution, false);
        UUID userId = UUID.randomUUID();
        mockManager(manager(institution), classroom);
        when(memberRepository.findByClassroomIdAndUserId(classroom.getId(), userId)).thenReturn(Optional.empty());

        assertThrows(ClassroomNotFoundException.class,
                () -> service.removeMember(EMAIL, classroom.getId(), userId));
    }

    // adicionar professor

    @Test
    @DisplayName("Gestor adiciona professor da mesma instituição e ele recebe e-mail")
    void managerAddsTeacherAndNotifies() {
        InstitutionModel institution = institution(true);
        Teacher teacher = teacher(institution);
        ClassroomModel classroom = classroom(institution, false);
        mockManager(manager(institution), classroom);
        when(userRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));

        service.addMember(EMAIL, classroom.getId(), teacher.getId());

        ArgumentCaptor<ClassroomMember> captor = ArgumentCaptor.forClass(ClassroomMember.class);
        verify(memberRepository).save(captor.capture());
        assertSame(teacher, captor.getValue().getUser());
        verify(notifierService).sendClassroomTeacherAdded(TEACHER_EMAIL, "Turma", "2026/01");
    }

    @Test
    @DisplayName("Professor que já é membro não é adicionado nem avisado de novo")
    void doesNotAddTeacherTwice() {
        InstitutionModel institution = institution(true);
        Teacher teacher = teacher(institution);
        ClassroomModel classroom = classroom(institution, false);
        mockManager(manager(institution), classroom);
        when(userRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));
        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), teacher.getId())).thenReturn(true);

        service.addMember(EMAIL, classroom.getId(), teacher.getId());

        verify(memberRepository, never()).save(any());
        verify(notifierService, never()).sendClassroomTeacherAdded(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Gestor não adiciona professor de outra instituição")
    void managerCannotAddTeacherOfOtherInstitution() {
        InstitutionModel institution = institution(true);
        Teacher teacher = teacher(institution(true));
        ClassroomModel classroom = classroom(institution, false);
        mockManager(manager(institution), classroom);
        when(userRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));

        assertThrows(ClassroomForbiddenException.class,
                () -> service.addMember(EMAIL, classroom.getId(), teacher.getId()));

        verify(memberRepository, never()).save(any());
        verifyNoInteractions(notifierService);
    }

    @Test
    @DisplayName("Gestor não adiciona aluno pela rota de professor")
    void managerCannotAddStudentAsTeacher() {
        InstitutionModel institution = institution(true);
        Student student = student(institution);
        ClassroomModel classroom = classroom(institution, false);
        mockManager(manager(institution), classroom);
        when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));

        assertThrows(ClassroomForbiddenException.class,
                () -> service.addMember(EMAIL, classroom.getId(), student.getId()));

        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Quem não é gestor da instituição não adiciona professor")
    void nonManagerCannotAddTeacher() {
        InstitutionModel institution = institution(true);
        ClassroomModel classroom = classroom(institution, false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(teacher(institution)));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));

        assertThrows(ClassroomForbiddenException.class,
                () -> service.addMember(EMAIL, classroom.getId(), UUID.randomUUID()));

        verify(memberRepository, never()).save(any());
    }

    // apoio

    private void mockManager(Manager manager, ClassroomModel classroom) {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(manager));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));
    }

    private void mockTeacherMember(Teacher teacher, ClassroomModel classroom, boolean member) {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(teacher));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), teacher.getId())).thenReturn(member);
    }

    private void mockJoin(User user, ClassroomModel classroom) {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(codeService.findClassroomId(CODE)).thenReturn(Optional.of(classroom.getId()));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));
    }

    private InstitutionModel institution(boolean active) {
        return InstitutionModel.builder().id(UUID.randomUUID()).active(active).build();
    }

    private Admin admin() {
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        return admin;
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
        teacher.setEmail(TEACHER_EMAIL);
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

    private ClassroomMember member(ClassroomModel classroom, User user) {
        return ClassroomMember.builder()
                .classroom(classroom)
                .user(user)
                .build();
    }
}
