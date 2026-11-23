package com.n0hana.echoes_server.classroom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.n0hana.echoes_server.auth.attempt.AttemptService;
import com.n0hana.echoes_server.classroom.exception.ClassroomArchivedException;
import com.n0hana.echoes_server.classroom.exception.ClassroomForbiddenException;
import com.n0hana.echoes_server.classroom.material.ClassroomMaterialRepository;
import com.n0hana.echoes_server.classroom.material.ClassroomMaterialStorage;
import com.n0hana.echoes_server.infra.javers.checkpoint.JaversCheckpointService;
import com.n0hana.echoes_server.infra.javers.recovery.DeletionRecoveryService;
import com.n0hana.echoes_server.infra.notifier.NotifierService;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;

@ExtendWith(MockitoExtension.class)
class ClassroomServiceCoverageTests {

    private static final String EMAIL = "usuario@exemplo.test";
    private static final Pageable PAGEABLE = PageRequest.of(0, 10);

    @Mock private ClassroomRepository classroomRepository;
    @Mock private ClassroomMemberRepository memberRepository;
    @Mock private UserRepository userRepository;
    @Mock private ClassroomCodeService codeService;
    @Mock private NotifierService notifierService;
    @Mock private ClassroomMaterialRepository materialRepository;
    @Mock private ClassroomMaterialStorage materialStorage;
    @Mock private AttemptService attempts;
    @Mock private JaversCheckpointService checkpoints;
    @Mock private DeletionRecoveryService recovery;

    @InjectMocks private ClassroomService service;

    @Test
    @DisplayName("Gestor sem instituição não cria turma")
    void managerWithoutInstitutionCannotCreate() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(manager(null)));

        assertThrows(ClassroomForbiddenException.class,
                () -> service.create(EMAIL, new ClassroomDTO.Request("T", null, "2026/01")));
        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Usuário inexistente gera UserNotFoundException")
    void unknownUserFails() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.findById(EMAIL, UUID.randomUUID()));
    }

    @Test
    @DisplayName("Gestor sem instituição lista página vazia")
    void managerWithoutInstitutionListsEmptyPage() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(manager(null)));

        Page<ClassroomModel> page = service.findAll(EMAIL, "", PAGEABLE);

        assertTrue(page.isEmpty());
    }

    @Test
    @DisplayName("Aluno lista só as turmas em que participa")
    void studentListsOwnClassrooms() {
        Student student = student(institution());
        Page<ClassroomModel> page = Page.empty(PAGEABLE);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(classroomRepository.findByMemberId(student.getId(), "x", PAGEABLE)).thenReturn(page);

        assertSame(page, service.findAll(EMAIL, "x", PAGEABLE));
    }

    @Test
    @DisplayName("Turma arquivada não pode ser alterada")
    void archivedClassroomCannotBeUpdated() {
        InstitutionModel inst = institution();
        ClassroomModel classroom = classroom(inst, true);
        mockManager(manager(inst), classroom);

        assertThrows(ClassroomArchivedException.class,
                () -> service.update(EMAIL, classroom.getId(), new ClassroomDTO.Request("N", "d", "2026/02")));
        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Gestor de outra instituição não gerencia a turma")
    void managerFromOtherInstitutionIsForbidden() {
        ClassroomModel classroom = classroom(institution(), false);
        mockManager(manager(institution()), classroom);

        assertThrows(ClassroomForbiddenException.class, () -> service.archive(EMAIL, classroom.getId()));
    }

    @Test
    @DisplayName("Admin não é gestor: não gerencia turma")
    void adminIsNotManager() {
        ClassroomModel classroom = classroom(institution(), false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(admin()));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));

        assertThrows(ClassroomForbiddenException.class, () -> service.unarchive(EMAIL, classroom.getId()));
    }

    @Test
    @DisplayName("Gestor sem instituição não gerencia turma")
    void managerWithoutInstitutionIsForbidden() {
        ClassroomModel classroom = classroom(institution(), false);
        mockManager(manager(null), classroom);

        assertThrows(ClassroomForbiddenException.class, () -> service.delete(EMAIL, classroom.getId()));
    }

    @Test
    @DisplayName("Arquivar e desarquivar alteram a marca da turma")
    void archiveAndUnarchive() {
        InstitutionModel inst = institution();
        ClassroomModel classroom = classroom(inst, false);
        mockManager(manager(inst), classroom);
        when(classroomRepository.save(any(ClassroomModel.class))).thenAnswer(i -> i.getArgument(0));

        assertTrue(service.archive(EMAIL, classroom.getId()).isArchived());
        assertFalse(service.unarchive(EMAIL, classroom.getId()).isArchived());
    }

    @Test
    @DisplayName("Admin vê a turma, usuário sem vínculo não")
    void canViewRules() {
        ClassroomModel classroom = classroom(institution(), false);
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(admin()));
        assertSame(classroom, service.findById(EMAIL, classroom.getId()));

        Student outsider = student(institution());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(outsider));
        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), outsider.getId())).thenReturn(false);
        assertThrows(ClassroomForbiddenException.class, () -> service.findById(EMAIL, classroom.getId()));
    }

    @Test
    @DisplayName("Aluno vê os membros sem e-mail, gestor com e-mail")
    void studentMembersHaveEmailHidden() {
        InstitutionModel inst = institution();
        ClassroomModel classroom = classroom(inst, false);
        Student student = student(inst);
        student.setEmail("aluno@exemplo.test");
        ClassroomMember member = ClassroomMember.builder().classroom(classroom).user(student)
                .joinedAt(LocalDateTime.now()).build();
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(memberRepository.findByClassroomId(eq(classroom.getId()), eq(PAGEABLE)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(member)));

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), student.getId())).thenReturn(true);
        assertNull(service.findMembers(EMAIL, classroom.getId(), PAGEABLE).getContent().get(0).email());

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(manager(inst)));
        assertEquals("aluno@exemplo.test",
                service.findMembers(EMAIL, classroom.getId(), PAGEABLE).getContent().get(0).email());
    }

    @Test
    @DisplayName("Quem não vê a turma não lista membros")
    void outsiderCannotListMembers() {
        ClassroomModel classroom = classroom(institution(), false);
        Student outsider = student(institution());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(outsider));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));

        assertThrows(ClassroomForbiddenException.class,
                () -> service.findMembers(EMAIL, classroom.getId(), PAGEABLE));
    }

    @Test
    @DisplayName("Código de entrada só aparece para professor membro")
    void joinCodeOnlyForTeacherMember() {
        InstitutionModel inst = institution();
        ClassroomModel classroom = classroom(inst, false);
        Student student = student(inst);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        assertNull(service.findJoinCode(EMAIL, classroom.getId()));

        Teacher teacher = teacher(inst);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(teacher));
        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), teacher.getId())).thenReturn(true);
        when(codeService.findCode(classroom.getId())).thenReturn(Optional.of("ABC234"));
        assertEquals("ABC234", service.findJoinCode(EMAIL, classroom.getId()));

        when(codeService.findCode(classroom.getId())).thenReturn(Optional.empty());
        assertNull(service.findJoinCode(EMAIL, classroom.getId()));
    }

    @Test
    @DisplayName("Excluir turma remove materiais e arquivos depois do commit")
    void deleteRemovesMaterials() {
        InstitutionModel inst = institution();
        ClassroomModel classroom = classroom(inst, false);
        mockManager(manager(inst), classroom);
        when(recovery.recordClassroomDeletion(classroom)).thenReturn(List.of());
        when(materialRepository.findStoredNamesByClassroomId(classroom.getId())).thenReturn(List.of("a.pdf"));

        ClassroomModel result = service.delete(EMAIL, classroom.getId());

        assertTrue(result.isDeleted());
        verify(materialRepository).deleteAllByClassroomId(classroom.getId());
        verify(materialStorage).deleteAfterCommit(List.of("a.pdf"));
        verify(classroomRepository).save(classroom);
    }

    @Test
    @DisplayName("Limpar alunos acima do limite cria checkpoint; professores ficam")
    void clearStudentsAboveThresholdCreatesCheckpoint() {
        InstitutionModel inst = institution();
        ClassroomModel classroom = classroom(inst, false);
        mockManager(manager(inst), classroom);
        List<ClassroomMember> members = new ArrayList<>();
        for (int i = 0; i < 11; i++)
            members.add(ClassroomMember.builder().classroom(classroom).user(studentWithRole(inst)).build());
        members.add(ClassroomMember.builder().classroom(classroom).user(teacherWithRole(inst)).build());
        when(memberRepository.findByClassroomId(classroom.getId())).thenReturn(members);

        service.clearStudents(EMAIL, classroom.getId());

        verify(checkpoints).create(eq(ClassroomMember.class.getName()), anyList(), eq(EMAIL));
        verify(memberRepository).deleteAll(members.subList(0, 11));
    }

    @Test
    @DisplayName("Limpar poucos alunos não cria checkpoint")
    void clearStudentsBelowThresholdSkipsCheckpoint() {
        InstitutionModel inst = institution();
        ClassroomModel classroom = classroom(inst, false);
        mockManager(manager(inst), classroom);
        ClassroomMember one = ClassroomMember.builder().classroom(classroom).user(studentWithRole(inst)).build();
        when(memberRepository.findByClassroomId(classroom.getId())).thenReturn(List.of(one));

        service.clearStudents(EMAIL, classroom.getId());

        verify(checkpoints, never()).create(any(), anyList(), any());
        verify(memberRepository).deleteAll(List.of(one));
    }

    @Test
    @DisplayName("Gerar código: só professor, turma ativa e sem código vigente")
    void generateCodeRules() {
        InstitutionModel inst = institution();
        ClassroomModel classroom = classroom(inst, false);
        Teacher teacher = teacher(inst);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(teacher));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), teacher.getId())).thenReturn(true);
        when(codeService.findCode(classroom.getId())).thenReturn(Optional.empty());
        when(codeService.generate(classroom.getId())).thenReturn("ABC234");
        when(codeService.secondsLeft("ABC234")).thenReturn(600L);

        ClassroomDTO.CodeResponse response = service.generateCode(EMAIL, classroom.getId());
        assertEquals("ABC234", response.code());
        assertEquals(600L, response.expiresInSeconds());

        when(codeService.findCode(classroom.getId())).thenReturn(Optional.of("ABC234"));
        assertThrows(com.n0hana.echoes_server.classroom.exception.ClassroomCodeActiveException.class,
                () -> service.generateCode(EMAIL, classroom.getId()));

        classroom.setArchived(true);
        assertThrows(ClassroomArchivedException.class, () -> service.generateCode(EMAIL, classroom.getId()));

        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), teacher.getId())).thenReturn(false);
        assertThrows(ClassroomForbiddenException.class, () -> service.generateCode(EMAIL, classroom.getId()));
    }

    @Test
    @DisplayName("Entrada: professor, instituição nula ou outra instituição são recusados e registrados")
    void joinRejectsInvalidUsers() {
        InstitutionModel inst = institution();
        ClassroomModel classroom = classroom(inst, false);
        when(codeService.findClassroomId("ABC234")).thenReturn(Optional.of(classroom.getId()));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));

        for (User user : new User[] { teacher(inst), student(null), student(institution()), admin() }) {
            user.setEmail(EMAIL);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            assertThrows(ClassroomForbiddenException.class,
                    () -> service.join(EMAIL, new ClassroomDTO.JoinRequest(" abc234 ")));
        }
        verify(attempts, org.mockito.Mockito.times(4)).register(any(), eq(EMAIL));
    }

    @Test
    @DisplayName("Entrada: código desconhecido é recusado")
    void joinRejectsUnknownCode() {
        Student student = student(institution());
        student.setEmail(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(codeService.findClassroomId("ABC234")).thenReturn(Optional.empty());

        assertThrows(ClassroomForbiddenException.class,
                () -> service.join(EMAIL, new ClassroomDTO.JoinRequest("abc234")));
    }

    @Test
    @DisplayName("Entrada: membro devolve a turma, matriculado em outra falha, arquivada falha, senão salva")
    void joinMembershipBranches() {
        InstitutionModel inst = institution();
        ClassroomModel classroom = classroom(inst, false);
        Student student = student(inst);
        student.setEmail(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(codeService.findClassroomId("ABC234")).thenReturn(Optional.of(classroom.getId()));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        ClassroomDTO.JoinRequest dto = new ClassroomDTO.JoinRequest("ABC234");

        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), student.getId())).thenReturn(true);
        assertSame(classroom, service.join(EMAIL, dto));
        verify(memberRepository, never()).save(any());

        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), student.getId())).thenReturn(false);
        when(memberRepository.existsByUserId(student.getId())).thenReturn(true);
        assertThrows(com.n0hana.echoes_server.classroom.exception.ClassroomStudentEnrolledException.class,
                () -> service.join(EMAIL, dto));

        when(memberRepository.existsByUserId(student.getId())).thenReturn(false);
        classroom.setArchived(true);
        assertThrows(ClassroomArchivedException.class, () -> service.join(EMAIL, dto));

        classroom.setArchived(false);
        assertSame(classroom, service.join(EMAIL, dto));
        verify(memberRepository).save(any(ClassroomMember.class));
    }

    @Test
    @DisplayName("Sair e remover membro: falham quando o vínculo não existe")
    void leaveAndRemoveMemberWithoutLink() {
        InstitutionModel inst = institution();
        ClassroomModel classroom = classroom(inst, false);
        Manager manager = manager(inst);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(manager));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(memberRepository.findByClassroomIdAndUserId(eq(classroom.getId()), any())).thenReturn(Optional.empty());

        assertThrows(ClassroomForbiddenException.class, () -> service.leave(EMAIL, classroom.getId()));
        assertThrows(com.n0hana.echoes_server.classroom.exception.ClassroomNotFoundException.class,
                () -> service.removeMember(EMAIL, classroom.getId(), UUID.randomUUID()));
    }

    @Test
    @DisplayName("Adicionar professor: valida tipo, atividade e instituição; não duplica")
    void addMemberBranches() {
        InstitutionModel inst = institution();
        ClassroomModel classroom = classroom(inst, false);
        mockManager(manager(inst), classroom);
        UUID teacherId = UUID.randomUUID();

        Teacher teacher = teacher(inst);
        teacher.setActive(true);
        teacher.setEmail("prof@exemplo.test");
        when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));
        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), teacherId)).thenReturn(true);
        service.addMember(EMAIL, classroom.getId(), teacherId);
        verify(memberRepository, never()).save(any());

        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), teacherId)).thenReturn(false);
        service.addMember(EMAIL, classroom.getId(), teacherId);
        verify(memberRepository).save(any(ClassroomMember.class));
        verify(notifierService).sendClassroomTeacherAdded("prof@exemplo.test", "Turma", "2026/01");

        Teacher inactive = teacher(inst);
        inactive.setActive(false);
        Teacher otherInst = teacher(institution());
        otherInst.setActive(true);
        Teacher noInst = teacher(null);
        noInst.setActive(true);
        Student notTeacher = student(inst);
        for (User invalid : new User[] { inactive, otherInst, noInst, notTeacher, admin() }) {
            when(userRepository.findById(teacherId)).thenReturn(Optional.of(invalid));
            assertThrows(ClassroomForbiddenException.class,
                    () -> service.addMember(EMAIL, classroom.getId(), teacherId));
        }
    }

    @Test
    @DisplayName("Professor ausente falha ao adicionar membro")
    void addMemberUnknownTeacher() {
        InstitutionModel inst = institution();
        ClassroomModel classroom = classroom(inst, false);
        mockManager(manager(inst), classroom);
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> service.addMember(EMAIL, classroom.getId(), UUID.randomUUID()));
    }

    @Test
    @DisplayName("Materiais: só professor membro de turma não arquivada")
    void requireTeacherForMaterials() {
        InstitutionModel inst = institution();
        ClassroomModel classroom = classroom(inst, false);
        Teacher teacher = teacher(inst);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(teacher));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), teacher.getId())).thenReturn(true);

        assertSame(classroom, service.requireTeacherForMaterials(EMAIL, classroom.getId()));

        classroom.setArchived(true);
        assertThrows(ClassroomArchivedException.class,
                () -> service.requireTeacherForMaterials(EMAIL, classroom.getId()));

        classroom.setArchived(false);
        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), teacher.getId())).thenReturn(false);
        assertThrows(ClassroomForbiddenException.class,
                () -> service.requireTeacherForMaterials(EMAIL, classroom.getId()));
    }

    // apoio

    private void mockManager(Manager manager, ClassroomModel classroom) {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(manager));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));
    }

    private InstitutionModel institution() {
        return InstitutionModel.builder().id(UUID.randomUUID()).active(true).build();
    }

    private Admin admin() {
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        return admin;
    }

    private Manager manager(InstitutionModel institution) {
        Manager manager = new Manager();
        manager.setId(UUID.randomUUID());
        manager.setInstitution(institution);
        return manager;
    }

    private Teacher teacher(InstitutionModel institution) {
        Teacher teacher = new Teacher();
        teacher.setId(UUID.randomUUID());
        teacher.setInstitution(institution);
        return teacher;
    }

    private Student student(InstitutionModel institution) {
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setInstitution(institution);
        return student;
    }

    private Student studentWithRole(InstitutionModel institution) {
        Student student = student(institution);
        return student;
    }

    private Teacher teacherWithRole(InstitutionModel institution) {
        Teacher teacher = teacher(institution);
        return teacher;
    }

    private ClassroomModel classroom(InstitutionModel institution, boolean archived) {
        return ClassroomModel.builder().id(UUID.randomUUID()).title("Turma").period("2026/01")
                .institution(institution).archived(archived).build();
    }
}
