package com.n0hana.echoes_server.classroom;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.classroom.material.ClassroomMaterialRepository;
import com.n0hana.echoes_server.classroom.material.ClassroomMaterialStorage;
import com.n0hana.echoes_server.classroom.exception.ClassroomArchivedException;
import com.n0hana.echoes_server.classroom.exception.ClassroomCodeActiveException;
import com.n0hana.echoes_server.classroom.exception.ClassroomForbiddenException;
import com.n0hana.echoes_server.classroom.exception.ClassroomNotFoundException;
import com.n0hana.echoes_server.classroom.exception.ClassroomStudentEnrolledException;
import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.infra.notifier.NotifierService;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;
import com.n0hana.echoes_server.user.model.UserRole;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClassroomService {

    private static final String INVALID_CODE = "Código inválido ou expirado";

    private final ClassroomRepository classroomRepository;
    private final ClassroomMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ClassroomCodeService codeService;
    private final NotifierService notifierService;
    private final ClassroomMaterialRepository materialRepository;
    private final ClassroomMaterialStorage materialStorage;

    @Transactional
    @Auditable(action = "CREATE", entity = "Classroom", entityId = "#result.id", entityName = "#result.title", institutionId = "#result.institution.id")
    public ClassroomModel create(String email, ClassroomDTO.Request dto) {
        User user = findUser(email);
        InstitutionModel institution = institutionOf(user);

        // só gestor de instituição ativa cria turma
        if (!(user instanceof Manager) || institution == null || !institution.isActive())
            throw new ClassroomForbiddenException("Apenas o gestor de uma instituição ativa cria turmas");

        return classroomRepository.save(ClassroomModel.builder()
                .title(dto.title())
                .description(dto.description())
                .period(dto.period())
                .institution(institution)
                .build());
    }

    @Transactional(readOnly = true)
    public Page<ClassroomModel> findAll(String email, String name, Pageable pageable) {
        User user = findUser(email);

        // admin vê todas, gestor as da instituição, professor e aluno só as que participam
        if (user instanceof Admin)
            return classroomRepository.findByTitleContainingIgnoreCase(name, pageable);

        if (user instanceof Manager) {
            InstitutionModel institution = institutionOf(user);
            if (institution == null)
                return Page.empty(pageable);
            return classroomRepository.findByInstitutionIdAndTitleContainingIgnoreCase(institution.getId(), name,
                    pageable);
        }
        return classroomRepository.findByMemberId(user.getId(), name, pageable);
    }

    @Transactional(readOnly = true)
    public ClassroomModel findById(String email, UUID id) {
        User user = findUser(email);
        ClassroomModel classroom = findClassroom(id);

        if (!canView(user, classroom))
            throw new ClassroomForbiddenException("Sem acesso a esta turma");

        return classroom;
    }

    // só professor da turma enxerga o código de entrada ativo
    @Transactional(readOnly = true)
    public String findJoinCode(String email, UUID id) {
        User user = findUser(email);

        if (!isTeacherMember(user, id))
            return null;

        return codeService.findCode(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public Page<ClassroomDTO.MemberInfo> findMembers(String email, UUID id, Pageable pageable) {
        User user = findUser(email);
        ClassroomModel classroom = findClassroom(id);

        if (!canView(user, classroom))
            throw new ClassroomForbiddenException("Sem acesso a esta turma");

        return memberRepository.findByClassroomId(id, pageable).map(ClassroomDTO.MemberInfo::from);
    }

    @Transactional
    @Auditable(action = "UPDATE", entity = "Classroom", entityId = "#id", entityName = "#result.title", institutionId = "#result.institution.id")
    public ClassroomModel update(String email, UUID id, ClassroomDTO.Request dto) {
        ClassroomModel classroom = findManagedClassroom(email, id);

        if (classroom.isArchived())
            throw new ClassroomArchivedException();

        classroom.setTitle(dto.title());
        classroom.setDescription(dto.description());
        classroom.setPeriod(dto.period());
        return classroomRepository.save(classroom);
    }

    @Transactional
    @Auditable(action = "ARCHIVE", entity = "Classroom", entityId = "#id", entityName = "#result.title", institutionId = "#result.institution.id")
    public ClassroomModel archive(String email, UUID id) {
        ClassroomModel classroom = findManagedClassroom(email, id);
        classroom.setArchived(true);
        return classroomRepository.save(classroom);
    }

    @Transactional
    @Auditable(action = "UNARCHIVE", entity = "Classroom", entityId = "#id", entityName = "#result.title", institutionId = "#result.institution.id")
    public ClassroomModel unarchive(String email, UUID id) {
        ClassroomModel classroom = findManagedClassroom(email, id);
        classroom.setArchived(false);
        return classroomRepository.save(classroom);
    }

    // exclusão lógica: remove os vínculos dos membros e marca a turma como excluída
    @Transactional
    @Auditable(action = "DELETE", entity = "Classroom", entityId = "#id", entityName = "#result.title", institutionId = "#result.institution.id")
    public ClassroomModel delete(String email, UUID id) {
        ClassroomModel classroom = findManagedClassroom(email, id);
        memberRepository.deleteByClassroomId(id);
        // A exclusão da turma é lógica, mas os materiais e seus arquivos são removidos.
        var storedNames = materialRepository.findStoredNamesByClassroomId(id);
        materialRepository.deleteAllByClassroomId(id);
        materialStorage.deleteAfterCommit(storedNames);
        classroom.setDeleted(true);
        classroomRepository.save(classroom);
        return classroom;
    }

    // limpa a turma para o próximo semestre, professores ficam
    @Transactional
    @Auditable(action = "CLEAR_STUDENTS", entity = "Classroom", entityId = "#id", entityName = "#result.title", institutionId = "#result.institution.id")
    public ClassroomModel clearStudents(String email, UUID id) {
        ClassroomModel classroom = findManagedClassroom(email, id);

        // o usuário do membro pode ser um proxy, por isso olha o papel em vez de instanceof
        List<ClassroomMember> students = memberRepository.findByClassroomId(id).stream()
                .filter(member -> member.getUser().getUserRole() == UserRole.STUDENT)
                .toList();

        memberRepository.deleteAll(students);
        return classroom;
    }

    // só gera um novo código depois que o anterior expirou
    @Transactional
    @Auditable(action = "GENERATE_CODE", entity = "Classroom", entityId = "#id")
    public ClassroomDTO.CodeResponse generateCode(String email, UUID id) {
        User user = findUser(email);
        ClassroomModel classroom = findClassroom(id);

        if (!isTeacherMember(user, id))
            throw new ClassroomForbiddenException("Apenas professores da turma podem gerar o código");

        if (classroom.isArchived())
            throw new ClassroomArchivedException("Turma arquivada não gera código de entrada");

        if (codeService.findCode(id).isPresent())
            throw new ClassroomCodeActiveException();

        String code = codeService.generate(id);
        return new ClassroomDTO.CodeResponse(code, codeService.secondsLeft(code));
    }

    @Transactional
    @Auditable(action = "JOIN", entity = "Classroom", entityId = "#result.id", entityName = "#result.title", institutionId = "#result.institution.id")
    public ClassroomModel join(String email, ClassroomDTO.JoinRequest dto) {
        User user = findUser(email);
        String code = dto.code().trim().toUpperCase();

        // código inexistente, expirado ou de outra instituição respondem igual
        ClassroomModel classroom = codeService.findClassroomId(code)
                .flatMap(classroomRepository::findById)
                .orElseThrow(() -> new ClassroomForbiddenException(INVALID_CODE));

        InstitutionModel institution = institutionOf(user);
        if (!(user instanceof Student) || institution == null
                || !institution.getId().equals(classroom.getInstitution().getId()))
            throw new ClassroomForbiddenException(INVALID_CODE);

        // quem já é membro desta turma recebe a turma sem erro
        if (memberRepository.existsByClassroomIdAndUserId(classroom.getId(), user.getId()))
            return classroom;

        // aluno só pode estar em uma turma
        if (memberRepository.existsByUserId(user.getId()))
            throw new ClassroomStudentEnrolledException();

        if (classroom.isArchived())
            throw new ClassroomArchivedException("Turma arquivada não aceita novos alunos");

        memberRepository.save(ClassroomMember.builder()
                .classroom(classroom)
                .user(user)
                .studentId(user.getId())
                .build());
        return classroom;
    }

    @Transactional
    @Auditable(action = "LEAVE", entity = "Classroom", entityId = "#id", entityName = "#result.title", institutionId = "#result.institution.id")
    public ClassroomModel leave(String email, UUID id) {
        User user = findUser(email);
        ClassroomModel classroom = findClassroom(id);

        ClassroomMember member = memberRepository.findByClassroomIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ClassroomForbiddenException("Você não é membro desta turma"));

        memberRepository.delete(member);
        return classroom;
    }

    // gestor remove aluno ou professor de turma da própria instituição
    @Transactional
    @Auditable(action = "REMOVE_MEMBER", entity = "Classroom", entityId = "#id", entityName = "#result.title", institutionId = "#result.institution.id")
    public ClassroomModel removeMember(String email, UUID id, UUID userId) {
        ClassroomModel classroom = findManagedClassroom(email, id);

        ClassroomMember member = memberRepository.findByClassroomIdAndUserId(id, userId)
                .orElseThrow(() -> new ClassroomNotFoundException("Membro não encontrado na turma"));

        memberRepository.delete(member);
        return classroom;
    }

    // gestor adiciona professor da mesma instituição e o professor recebe um e-mail
    @Transactional
    @Auditable(action = "ADD_MEMBER", entity = "Classroom", entityId = "#id", entityName = "#result.title", institutionId = "#result.institution.id")
    public ClassroomModel addMember(String email, UUID id, UUID userId) {
        ClassroomModel classroom = findManagedClassroom(email, id);

        User teacher = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        InstitutionModel institution = institutionOf(teacher);

        if (!(teacher instanceof Teacher) || !teacher.isActive() || institution == null
                || !institution.getId().equals(classroom.getInstitution().getId()))
            throw new ClassroomForbiddenException("Professor inválido para esta turma");

        if (!memberRepository.existsByClassroomIdAndUserId(id, userId)) {
            memberRepository.save(ClassroomMember.builder()
                    .classroom(classroom)
                    .user(teacher)
                    .build());

            notifierService.sendClassroomTeacherAdded(teacher.getEmail(), classroom.getTitle(),
                    classroom.getPeriod());
        }

        return classroom;
    }

    // busca a turma e garante que quem pediu é o gestor da instituição dela
    private ClassroomModel findManagedClassroom(String email, UUID id) {
        User user = findUser(email);
        ClassroomModel classroom = findClassroom(id);

        if (!isManagerOf(user, classroom))
            throw new ClassroomForbiddenException("Apenas o gestor da instituição pode executar esta ação");

        return classroom;
    }

    // Verificação compartilhada com o módulo de materiais. Só professor MEMBRO de turma não arquivada.
    @Transactional(readOnly = true)
    public ClassroomModel requireTeacherForMaterials(String email, UUID id) {
        User user = findUser(email);
        ClassroomModel classroom = findClassroom(id);
        if (!isTeacherMember(user, id))
            throw new ClassroomForbiddenException("Apenas professores da turma podem alterar materiais");
        if (classroom.isArchived())
            throw new ClassroomArchivedException();
        return classroom;
    }

    private boolean isTeacherMember(User user, UUID classroomId) {
        return user instanceof Teacher && memberRepository.existsByClassroomIdAndUserId(classroomId, user.getId());
    }

    private boolean isManagerOf(User user, ClassroomModel classroom) {
        if (!(user instanceof Manager))
            return false;
        InstitutionModel institution = institutionOf(user);
        return institution != null && institution.getId().equals(classroom.getInstitution().getId());
    }

    private boolean canView(User user, ClassroomModel classroom) {
        return user instanceof Admin
                || isManagerOf(user, classroom)
                || memberRepository.existsByClassroomIdAndUserId(classroom.getId(), user.getId());
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(UserNotFoundException::new);
    }

    private ClassroomModel findClassroom(UUID id) {
        return classroomRepository.findById(id).orElseThrow(ClassroomNotFoundException::new);
    }

    // a instituição fica nas subclasses, admin não tem
    private InstitutionModel institutionOf(User user) {
        if (user instanceof Manager manager)
            return manager.getInstitution();
        if (user instanceof Teacher teacher)
            return teacher.getInstitution();
        if (user instanceof Student student)
            return student.getInstitution();
        return null;
    }
}
