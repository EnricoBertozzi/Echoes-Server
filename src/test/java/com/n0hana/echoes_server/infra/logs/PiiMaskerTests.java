package com.n0hana.echoes_server.infra.logs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PiiMaskerTests {

    private static final String JWT =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTYifQ.c2lnbmF0dXJlYWJj";

    @Test
    @DisplayName("Mascara e-mail em qualquer posição do texto")
    void masksEmail() {
        String out = PiiMasker.mask("Senha redefinida para maria.silva+x@escola.edu.br e joao@a.com");

        assertFalse(out.contains("@escola"));
        assertFalse(out.contains("joao@"));
        assertEquals("Senha redefinida para " + PiiMasker.EMAIL + " e " + PiiMasker.EMAIL, out);
    }

    @Test
    @DisplayName("Mascara JWT, Bearer e pares senha=valor")
    void masksCredentials() {
        String out = PiiMasker.mask("falhou com Bearer " + JWT + " e token=abc123 password: \"minha senha\" senha=Xyz@9");

        assertFalse(out.contains("eyJ"));
        assertFalse(out.contains("abc123"));
        assertFalse(out.contains("minha senha"));
        assertFalse(out.contains("Xyz@9"));
        assertTrue(out.contains("Bearer " + PiiMasker.TOKEN));
    }

    @Test
    @DisplayName("JWT solto (sem Bearer) também é mascarado")
    void masksBareJwt() {
        assertEquals("x " + PiiMasker.TOKEN + " y", PiiMasker.mask("x " + JWT + " y"));
    }

    @Test
    @DisplayName("Mascara CPF formatado e preserva texto comum")
    void masksCpfOnly() {
        assertEquals("CPF " + PiiMasker.CPF, PiiMasker.mask("CPF 123.456.789-09"));
        assertEquals("Dispositivo AA:BB:CC não encontrado (id 42)", PiiMasker.mask("Dispositivo AA:BB:CC não encontrado (id 42)"));
    }

    @Test
    @DisplayName("Nulo e vazio passam; o limite de tamanho é aplicado depois do mascaramento")
    void nullAndLength() {
        assertNull(PiiMasker.mask(null));
        assertEquals("", PiiMasker.mask(""));
        assertEquals(10, PiiMasker.mask("a".repeat(500), 10).length());
        // o corte nunca deixa metade de um e-mail para trás: mascara primeiro
        assertFalse(PiiMasker.mask("x".repeat(240) + " maria@escola.com", 255).contains("@"));
    }

    @Test
    @DisplayName("A expressão SpEL do RESET_PASSWORD avalia para e-mail mascarado")
    void resetPasswordExpressionMasksEmail() throws Exception {
        String expression = com.n0hana.echoes_server.auth.password.PasswordService.class
                .getMethod("reset", String.class, String.class, String.class)
                .getAnnotation(Auditable.class).details();
        var context = new org.springframework.expression.spel.support.StandardEvaluationContext();
        context.setVariable("email", "maria.silva@escola.edu.br");

        String details = new org.springframework.expression.spel.standard.SpelExpressionParser()
                .parseExpression(expression).getValue(context, String.class);

        assertFalse(details.contains("maria.silva"), details);
        assertTrue(details.contains("@escola.edu.br"), details);
    }
}
