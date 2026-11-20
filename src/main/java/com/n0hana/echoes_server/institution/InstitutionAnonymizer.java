package com.n0hana.echoes_server.institution;

import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Anonimiza os dados identificáveis de uma instituição no momento do soft
 * delete.
 *
 * <p>
 * {@code name}, {@code acronym}, {@code cnpj} e {@code email} são obrigatórios
 * no banco, por isso recebem valores sentinelas em vez de {@code null}. O
 * {@code delete_token} gerado pela exclusão é único, o que mantém as
 * constraints {@code (cnpj, delete_token)} e {@code (email, delete_token)}
 * válidas e libera o CNPJ/e-mail originais para um novo cadastro.
 * </p>
 */
@Component
public class InstitutionAnonymizer {

    private static final String ANONYMIZED_NAME = "Instituição excluída";
    private static final String ANONYMIZED_ACRONYM = "EXCLUIDA";
    private static final String ANONYMIZED_CNPJ = "ANONIMIZADO";
    private static final String ANONYMIZED_EMAIL_PREFIX = "anonymous-";
    private static final String ANONYMIZED_EMAIL_DOMAIN = "@deleted.local";

    public void anonymize(InstitutionModel institution) {
        String suffix = UUID.randomUUID().toString();

        institution.setName(ANONYMIZED_NAME + " " + suffix);
        institution.setAcronym(ANONYMIZED_ACRONYM);
        institution.setCnpj(ANONYMIZED_CNPJ);
        institution.setEmail(ANONYMIZED_EMAIL_PREFIX + suffix + ANONYMIZED_EMAIL_DOMAIN);
        institution.setNomeFantasia(null);
        institution.setAddress(null);
        institution.setPhone(null);
        institution.setCep(null);
        institution.setLastVerificationAttemptAt(null);
        institution.setVerificationAttempts(0);
        institution.setActive(false);
    }
}
