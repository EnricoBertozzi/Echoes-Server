package com.n0hana.echoes_server.notifier;

import java.util.UUID;
import com.n0hana.echoes_server.institution.InstitutionModel;

public record InstitutionNotificationData(
    UUID id,
    String name,
    String cnpj,
    String acronym,
    String email,
    String address,
    String cep
) {
    public static InstitutionNotificationData from(InstitutionModel institution) {
        return new InstitutionNotificationData(
            institution.getId(),
            institution.getName(),
            institution.getCnpj(),
            institution.getAcronym(),
            institution.getEmail(),
            institution.getAddress(),
            institution.getCep()
        );
    }
}
