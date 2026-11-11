package com.n0hana.echoes_server.classroom;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.classroom.exception.ClassroomArchivedException;
import com.n0hana.echoes_server.classroom.exception.ClassroomForbiddenException;
import com.n0hana.echoes_server.classroom.exception.ClassroomNotFoundException;
import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClassroomService {

    private final ClassroomRepository classroomRepository;
    private final ClassroomMemberRepository memberRepository;
    private final UserRepository userRepository;

    @Transactional
    @Auditable(action = "CREATE", entity = "Classroom", entityId = "#result.id", institutionId = "#result.institution.id")
    public ClassroomModel create(String email, ClassroomDTO.Request dto) {
        User user = findUser(email);
        InstitutionModel institution = institutionOf(user);

        // só professor de instituição ativa cria turma
        if (!(user instanceof Teacher) || institution == null || !institution.isActive())
            throw new ClassroomForbiddenException("Professor sem instituição ativa");

        ClassroomModel classroom = classroomRepository.save(ClassroomModel.builder()
                .title(dto.title())
                .description(dto.description())
                .institution(institution)
                .build());

        // quem cria a turma vira moderador
        memberRepository.save(ClassroomMember.builder()
                .classroom(classroom)
                .user(user)
                .role(ClassroomMemberRole.MODERATOR)
                .build());

        return classroom;
    }

    @Transactional(readOnly = true)
    public Page<ClassroomModel> findAll(String email, Pageable pageable) {
        User user = findUser(email);

        // gestor vê todas da instituição, professor e aluno só as que participam
        if (user instanceof Manager) {
            InstitutionModel institution = institutionOf(user);
            if (institution == null)
                return Page.empty(pageable);
            return classroomRepository.findByInstitutionId(institution.getId(), pageable);
        }
        return classroomRepository.findByMemberId(user.getId(), pageable);
    }

    @Transactional(readOnly = true)
    public ClassroomModel findById(String email, UUID id) {
        User user = findUser(email);
        ClassroomModel classroom = findClassroom(id);

        if (!canView(user, classroom))
            throw new ClassroomForbiddenException("Sem acesso a esta turma");

        return classroom;
    }

    @Transactional
    @Auditable(action = "UPDATE", entity = "Classroom", entityId = "#id", institutionId = "#result.institution.id")
    public ClassroomModel update(String email, UUID id, ClassroomDTO.Request dto) {
        ClassroomModel classroom = findModeratedClassroom(email, id);

        if (classroom.isArchived())
            throw new ClassroomArchivedException();

        classroom.setTitle(dto.title());
        classroom.setDescription(dto.description());
        return classroomRepository.save(classroom);
    }

    @Transactional
    @Auditable(action = "ARCHIVE", entity = "Classroom", entityId = "#id", institutionId = "#result.institution.id")
    public ClassroomModel archive(String email, UUID id) {
        ClassroomModel classroom = findModeratedClassroom(email, id);
        classroom.setArchived(true);
        return classroomRepository.save(classroom);
    }

    @Transactional
    @Auditable(action = "UNARCHIVE", entity = "Classroom", entityId = "#id", institutionId = "#result.institution.id")
    public ClassroomModel unarchive(String email, UUID id) {
        ClassroomModel classroom = findModeratedClassroom(email, id);
        classroom.setArchived(false);
        return classroomRepository.save(classroom);
    }

    // busca a turma e garante que quem pediu é moderador dela
    private ClassroomModel findModeratedClassroom(String email, UUID id) {
        User user = findUser(email);
        ClassroomModel classroom = findClassroom(id);

        if (!memberRepository.existsByClassroomIdAndUserIdAndRole(id, user.getId(), ClassroomMemberRole.MODERATOR))
            throw new ClassroomForbiddenException("Apenas moderadores podem executar esta ação");

        return classroom;
    }

    private boolean canView(User user, ClassroomModel classroom) {
        if (user instanceof Manager) {
            InstitutionModel institution = institutionOf(user);
            return institution != null && institution.getId().equals(classroom.getInstitution().getId());
        }
        return memberRepository.existsByClassroomIdAndUserId(classroom.getId(), user.getId());
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
