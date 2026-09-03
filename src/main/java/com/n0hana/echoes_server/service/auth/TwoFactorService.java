package com.n0hana.echoes_server.service.auth;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class TwoFactorService {

    private final SecureRandom random = new SecureRandom();

    /**==================================
     *  CRIAÇÃO DO TOKEN MFA
     * ==================================
     * Após a autenticação primária o 
     * método é chamado para fornecidmento
     * do código único de validação
    */
    public String generateCode() {
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }
}
