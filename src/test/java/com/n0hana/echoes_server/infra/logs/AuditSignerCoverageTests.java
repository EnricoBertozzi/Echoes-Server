package com.n0hana.echoes_server.infra.logs;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("AuditSigner: assinatura HMAC dos registros")
class AuditSignerCoverageTests {

    private AuditSigner signer;

    @BeforeEach
    void setUp() {
        signer = new AuditSigner();
        ReflectionTestUtils.setField(signer, "key", "k".repeat(40) + UUID.randomUUID());
    }

    private AuditLog log() {
        AuditLog l = AuditLog.builder().userId("u1").action("CREATE").entity("Classroom").status("SUCCESS")
                .details("d").ip("192.0.2.1").entityId("e1").institutionId("i1").entityName("n")
                .actorName("a").actorRole("MANAGER").actorInstitutionName("inst").build();
        l.setTimestamp(Instant.parse("2025-01-01T10:00:00.900Z"));
        return l;
    }

    @Test
    @DisplayName("validateKey recusa chave nula ou curta e aceita 32+ bytes")
    void validateKey() {
        AuditSigner s = new AuditSigner();
        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(s, "validateKey"));
        ReflectionTestUtils.setField(s, "key", "curta");
        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(s, "validateKey"));
        ReflectionTestUtils.setField(s, "key", "x".repeat(32));
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(s, "validateKey"));
    }

    @Test
    @DisplayName("assinatura é determinística e verificável")
    void signVerify() {
        AuditLog l = log();
        String sig = signer.sign(l);
        l.setSignature(sig);

        assertEquals(sig, signer.sign(log()));
        assertTrue(signer.verify(l));
    }

    @Test
    @DisplayName("fração de segundo do timestamp não altera a assinatura")
    void truncaTimestamp() {
        AuditLog a = log();
        AuditLog b = log();
        b.setTimestamp(Instant.parse("2025-01-01T10:00:00.100Z"));

        assertEquals(signer.sign(a), signer.sign(b));
    }

    @Test
    @DisplayName("alterar qualquer campo invalida a assinatura")
    void adulteracao() {
        AuditLog l = log();
        l.setSignature(signer.sign(l));
        l.setDetails("outro");

        assertFalse(signer.verify(l));
    }

    @Test
    @DisplayName("registro sem assinatura não verifica")
    void semAssinatura() {
        assertFalse(signer.verify(log()));
    }

    @Test
    @DisplayName("campos nulos e timestamp nulo são aceitos; nulo difere de vazio com timestamp")
    void camposNulos() {
        AuditLog vazio = new AuditLog();
        String sig = signer.sign(vazio);

        assertNotEquals(null, sig);
        assertNotEquals(sig, signer.sign(log()));
    }

    @Test
    @DisplayName("sem chave configurada, sign falha com IllegalStateException")
    void semChave() {
        AuditSigner s = new AuditSigner();

        assertThrows(IllegalStateException.class, () -> s.sign(log()));
    }
}
