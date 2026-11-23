package com.n0hana.echoes_server.institution;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.user.dto.CreateInstitutionUserDTO;
import com.n0hana.echoes_server.user.service.UserRegisterService;

import lombok.RequiredArgsConstructor;

/**
 * Cadastro de instituição: a instituição só é criada junto com o convite do
 * seu primeiro gestor.
 */
@Service
@RequiredArgsConstructor
public class InstitutionRegistrationService {

    private final InstitutionService institutionService;
    private final UserRegisterService userRegisterService;

    // se o convite falhar (e-mail em uso, por exemplo), a instituição também não é criada
    @Transactional
    public InstitutionModel create(InstitutionModel model, String managerName, String managerEmail) {
        InstitutionModel saved = institutionService.create(model);
        userRegisterService.createManager(new CreateInstitutionUserDTO(managerName, managerEmail, saved.getId()));
        return saved;
    }
}
