package com.n0hana.echoes_server.infra.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionRepository;
import com.n0hana.echoes_server.institution.exception.InstitutionNotFoundException;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;
import com.n0hana.echoes_server.user.model.UserRole;

import lombok.RequiredArgsConstructor;

@Component("guard")
@RequiredArgsConstructor
public class AccessGuard {

    private final UserRepository userRepository;
    private final InstitutionRepository institutionRepository;

    /** admin, o próprio usuário ou manager da mesma instituição */
    public boolean canAccess(UUID targetId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // admin passa direto, sem ir ao banco
        if (isAdmin(auth)) {
            return true;
        }

        // sem login real ou sem alvo, nega (anonymousUser não é User)
        if (auth == null || !(auth.getPrincipal() instanceof User) || targetId == null) {
            return false;
        }

        User userLogged = (User) auth.getPrincipal();

        // o próprio registro
        if (userLogged.getId().equals(targetId)) {
            return true;
        }

        // só manager vê outro usuário
        if (userLogged.getUserRole() != UserRole.MANAGER) {
            return false;
        }

        InstitutionModel instituicaoUser = instituicaoDe(userLogged);

        // manager sem instituição, nega
        return instituicaoUser != null
                && userRepository.findById(targetId)
                        .map(alvo -> instituicaoUser.equals(instituicaoDe(alvo)))
                        .orElse(false);
    }

    /** é a instituição de quem está logado? admin passa */
    public boolean isInstituicaoUsuario(UUID institutionId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (isAdmin(auth)) {
            return true;
        }
        if (auth == null || !(auth.getPrincipal() instanceof User) || institutionId == null) {
            return false;
        }

        InstitutionModel institution = institutionRepository.findById(institutionId).orElseThrow(InstitutionNotFoundException::new);
        InstitutionModel studentInstitution = instituicaoDe((User) auth.getPrincipal());
        return institution.getId().equals(studentInstitution.getId());
    }

    private boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    // institutionId fica nas subclasses, admin não tem
    private InstitutionModel instituicaoDe(User usuario) {
        if (usuario instanceof Manager manager) {
            return manager.getInstitution();
        }
        if (usuario instanceof Teacher teacher) {
            return teacher.getInstitution();
        }
        if (usuario instanceof Student student) {
            return student.getInstitution();
        }
        return null; // admin
    }
}
