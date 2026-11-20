package com.n0hana.echoes_server.user;

import java.util.UUID;

import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.user.dto.UserDTO;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;

/**
 * Classe auxiliar para uso dos serviços relacionados aos usuários da aplicação
 * 
 * @author Enrico Bertozzi
 */
public class UserUtils {

    /**
     * Normaliza o e-mail enviado para manter sem espaços extras ou maiusculas
     * 
     * @param email E-mail a ser normalizazdo
     * @return E-mail normalizado
     */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    /**
     * Converte um usuário para um objeto DTO
     * 
     * @param user Usuário a ser convertido
     * @return DTO com dados do usuário
     */
    public static UserDTO toDTO(User user) {
        UUID institutionId = null;

        InstitutionModel institutionModel = institutionOf(user);
        if (institutionModel != null)
            institutionId = institutionModel.getId();

        return new UserDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                institutionId,
                user.getUserRole());
    }

    /**
     * Converte um usuário para um DTO com o e-mail mascarado, usado nas
     * listagens. Exemplo: maria@escola.com vira m***@escola.com.
     *
     * @param user Usuário a ser convertido
     * @return DTO com o e-mail mascarado
     */
    public static UserDTO toMaskedDTO(User user) {
        UserDTO dto = toDTO(user);

        return new UserDTO(
                dto.id(),
                dto.name(),
                maskEmail(dto.email()),
                dto.institutionId(),
                dto.role());
    }

    /**
     * Adiciona o id do usuário baseado na sua subclasse
     * 
     * @param user Instãncia de usuário
     * @return UUID da instituição.
     */
    public static InstitutionModel institutionOf(User user) {
        if (user instanceof Manager manager) {
            return manager.getInstitution();
        }
        if (user instanceof Teacher teacher) {
            return teacher.getInstitution();
        }
        if (user instanceof Student student) {
            return student.getInstitution();
        }
        return null;
    }

    /**
     * Mascara a parte local do e-mail, mantendo o domínio completo.
     *
     * @param email E-mail original (pode ser nulo)
     * @return E-mail mascarado ou nulo
     */
    private static String maskEmail(String email) {
        if (email == null) {
            return null;
        }

        int at = email.indexOf('@');
        if (at < 0) {
            return email;
        }

        String localPart = email.substring(0, at);
        String domain = email.substring(at);

        if (localPart.length() <= 1) {
            return "*" + domain;
        }

        return localPart.charAt(0) + "***" + domain;
    }
}
