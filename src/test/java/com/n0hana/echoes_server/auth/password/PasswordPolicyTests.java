package com.n0hana.echoes_server.auth.password;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.n0hana.echoes_server.auth.AuthDTO.LoginRequest;
import com.n0hana.echoes_server.auth.AuthDTO.MfaRequest;
import com.n0hana.echoes_server.user.dto.CompleteRegistrationDTO;

import jakarta.validation.Validation;

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

    @Test
    void registrationUsesTheSamePasswordLimitAndVerificationCodeFormat() {
        String atLimit = "Senha@1a" + "é".repeat(32);
        assertTrue(PasswordPolicy.isValid(atLimit));
        assertFalse(PasswordPolicy.isValid(atLimit + "a"));

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(
                    new CompleteRegistrationDTO("prof@example.com", atLimit, "123456", List.of())).isEmpty());
            assertEquals(Set.of("password", "code"), validator.validate(
                    new CompleteRegistrationDTO("prof@example.com", atLimit + "a", "1234567", List.of()))
                    .stream().map(violation -> violation.getPropertyPath().toString()).collect(Collectors.toSet()));
        }
    }

    @Test
    void loginAndMfaEnforceTheirOwnLimits() {
        String email = "prof@example.com";
        String emailAtLimit = "a".repeat(63) + "@" + String.join(".", "b".repeat(63), "c".repeat(63), "d".repeat(63));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(new LoginRequest(email, "a".repeat(72))).isEmpty());
            assertFalse(validator.validate(new LoginRequest(email, "a".repeat(73))).isEmpty());
            assertTrue(validator.validate(new LoginRequest(email, "é".repeat(36))).isEmpty());
            assertFalse(validator.validate(new LoginRequest(email, "é".repeat(36) + "a")).isEmpty());
            assertTrue(validator.validate(new LoginRequest(emailAtLimit, "senha")).isEmpty());
            assertFalse(validator.validate(new LoginRequest(emailAtLimit + ".e", "senha")).isEmpty());
            assertTrue(validator.validate(new MfaRequest(email, "123456")).isEmpty());
            assertFalse(validator.validate(new MfaRequest(email, "12345a")).isEmpty());
            assertFalse(validator.validate(new MfaRequest(email, "1234567")).isEmpty());
        }
    }
}
