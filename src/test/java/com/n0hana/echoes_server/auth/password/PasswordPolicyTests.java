package com.n0hana.echoes_server.auth.password;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordPolicyTests {

    @Test
    @DisplayName("Aceita senha com maiúscula, minúscula, número, símbolo e 8+ caracteres")
    void acceptsStrongPassword() {
        assertTrue(PasswordPolicy.isValid("Senha@123"));
    }

    @Test
    @DisplayName("Rejeita senhas fracas, curtas, nulas ou sem algum tipo de caractere (RNF07)")
    void rejectsWeakPasswords() {
        assertFalse(PasswordPolicy.isValid("1"));
        assertFalse(PasswordPolicy.isValid("senha"));
        assertFalse(PasswordPolicy.isValid("senha@123"));
        assertFalse(PasswordPolicy.isValid("SENHA@123"));
        assertFalse(PasswordPolicy.isValid("Senhaforte"));
        assertFalse(PasswordPolicy.isValid(null));
    }
}
