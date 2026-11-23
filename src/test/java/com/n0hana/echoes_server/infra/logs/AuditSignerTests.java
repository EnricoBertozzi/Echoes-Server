package com.n0hana.echoes_server.infra.logs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuditSignerTests {

    private AuditSigner signer(String key) {
        AuditSigner s = new AuditSigner();
        ReflectionTestUtils.setField(s, "key", key);
        return s;
    }

    private AuditLog sample() {
        AuditLog log = AuditLog.builder()
                .userId("u1").action("LOGIN_ATTEMPT").entity("User").status("FAILED")
                .details("Erro ao autenticar").ip("10.0.0.1").build();
        log.setTimestamp(Instant.parse("2026-11-20T10:00:00Z"));
        return log;
    }

    @Test
    @DisplayName("Registro assinado verifica; alterar qualquer campo invalida a assinatura (S15)")
    void signedRecordVerifiesAndTamperingIsDetected() {
        AuditSigner signer = signer("0123456789abcdef0123456789abcdef-test");
        AuditLog log = sample();
        log.setSignature(signer.sign(log));

        assertTrue(signer.verify(log));

        log.setStatus("SUCCESS");
        assertFalse(signer.verify(log));
    }

    @Test
    @DisplayName("Registro sem assinatura (anterior ao recurso) não verifica")
    void unsignedRecordDoesNotVerify() {
        assertFalse(signer("0123456789abcdef0123456789abcdef-test").verify(sample()));
    }

    @Test
    @DisplayName("Chave curta demais impede a inicialização")
    void shortKeyIsRejected() {
        AuditSigner signer = signer("curta");
        assertThrows(IllegalStateException.class, signer::validateKey);
    }
}
