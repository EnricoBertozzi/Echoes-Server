package com.n0hana.echoes_server.mfa;

import org.springframework.stereotype.Service;
import com.n0hana.echoes_server.infra.logs.Auditable;
import java.security.SecureRandom;

@Service
public class TwoFactorService {

    public static final String CODE_PATTERN = "[0-9]{6}";
    public static final String CODE_FORMAT_MESSAGE = "Informe um código de 6 dígitos";

    private final SecureRandom random = new SecureRandom();

    /**==================================
     *  CRIAÇÃO DO TOKEN MFA
     * ==================================
     * Após a autenticação primária o 
     * método é chamado para fornecidmento
     * do código único de validação
    */
    @Auditable(action = "GENERATED", entity = "MFA")
    public String generateCode() {
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }
}
