package com.n0hana.echoes_server.infra.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DataEncryptionKeysTests {

    private static final String JWT = "jwt-secret-com-mais-de-32-caracteres!!";
    private static final String AUDIT = "audit-hmac-com-mais-de-32-caracteres!!";
    private static final String KEY = AesGcmCipherTests.KEY_V1;

    private static DataEncryptionKeys keys(String key, String id, String old, String jwt, String audit) {
        return new DataEncryptionKeys(key, id, old, jwt, audit);
    }

    @Test
    @DisplayName("Chave válida: id ativo e chave antiga disponíveis")
    void validKeys() {
        DataEncryptionKeys k = keys(KEY, "v2", "v1:" + AesGcmCipherTests.KEY_V2, JWT, AUDIT);

        assertEquals("v2", k.activeKeyId());
        assertTrue(k.key("v1").isPresent());
        assertTrue(k.key("v9").isEmpty());
    }

    @Test
    @DisplayName("Falha no boot sem chave")
    void missingKey() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> keys("", "v1", "", JWT, AUDIT));
        assertTrue(e.getMessage().contains("DATA_ENCRYPTION_KEY"));
    }

    @Test
    @DisplayName("Falha com chave que não tem 32 bytes ou não é Base64")
    void wrongSizeOrEncoding() {
        String short16 = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class, () -> keys(short16, "v1", "", JWT, AUDIT));
        assertThrows(IllegalStateException.class, () -> keys("não é base64!", "v1", "", JWT, AUDIT));
    }

    @Test
    @DisplayName("Falha se a chave for igual ao JWT_SECRET ou ao AUDIT_HMAC_KEY")
    void mustDifferFromOtherSecrets() {
        assertThrows(IllegalStateException.class, () -> keys(KEY, "v1", "", KEY, AUDIT));
        assertThrows(IllegalStateException.class, () -> keys(KEY, "v1", "", JWT, KEY));
    }

    @Test
    @DisplayName("Falha com id de chave inválido ou chave antiga malformada/repetida")
    void invalidIdsAndOldKeys() {
        assertThrows(IllegalStateException.class, () -> keys(KEY, "v:1", "", JWT, AUDIT));
        assertThrows(IllegalStateException.class, () -> keys(KEY, "v1", "sem-separador", JWT, AUDIT));
        assertThrows(IllegalStateException.class, () -> keys(KEY, "v1", "v1:" + AesGcmCipherTests.KEY_V2, JWT, AUDIT));
    }
}
