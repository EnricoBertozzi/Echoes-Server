package com.n0hana.echoes_server.infra.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AesGcmCipherTests {

    static final String KEY_V1 = Base64.getEncoder().encodeToString("k".repeat(32).getBytes());
    static final String KEY_V2 = Base64.getEncoder().encodeToString("j".repeat(32).getBytes());
    private static final String CTX = "audit_log.ip";

    static DataEncryptionKeys keys(String key, String id, String old) {
        return new DataEncryptionKeys(key, id, old, "jwt-secret-com-mais-de-32-caracteres!!", "audit-hmac-com-mais-de-32-caracteres!!");
    }

    private final AesGcmCipher cipher = new AesGcmCipher(keys(KEY_V1, "v1", ""));

    @Test
    @DisplayName("Cifra e decifra o IP (IPv4 e IPv6)")
    void roundTrip() {
        for (String ip : new String[] { "203.0.113.7", "2001:0db8:85a3:0000:0000:8a2e:0370:7334" }) {
            String stored = cipher.encrypt(ip, CTX);

            assertTrue(stored.startsWith("enc:v1:"));
            assertFalse(stored.contains(ip), "o valor gravado não pode conter o IP em claro");
            assertEquals(ip, cipher.decrypt(stored, CTX));
        }
    }

    @Test
    @DisplayName("IP de IPv6 máximo cabe na coluna de 128 caracteres")
    void fitsColumn() {
        String longest = "ffff:ffff:ffff:ffff:ffff:ffff:255.255.255.255"; // 45 caracteres
        assertTrue(cipher.encrypt(longest, CTX).length() <= 128);
    }

    @Test
    @DisplayName("IV aleatório: o mesmo IP gera cifrados diferentes")
    void randomIv() {
        assertNotEquals(cipher.encrypt("203.0.113.7", CTX), cipher.encrypt("203.0.113.7", CTX));
    }

    @Test
    @DisplayName("Valor adulterado é rejeitado pela tag GCM")
    void tamperingIsDetected() {
        String stored = cipher.encrypt("203.0.113.7", CTX);
        String prefix = "enc:v1:";
        byte[] payload = Base64.getDecoder().decode(stored.substring(prefix.length()));
        payload[payload.length - 1] ^= 0x01;
        String tampered = prefix + Base64.getEncoder().encodeToString(payload);

        assertThrows(DataDecryptionException.class, () -> cipher.decrypt(tampered, CTX));
    }

    @Test
    @DisplayName("Valor copiado de outra coluna (outro AAD) não decifra")
    void contextIsBound() {
        String stored = cipher.encrypt("203.0.113.7", "user_terms_acceptance.ip_address");

        assertThrows(DataDecryptionException.class, () -> cipher.decrypt(stored, CTX));
    }

    @Test
    @DisplayName("Valor legado em claro e nulo passam sem alteração")
    void legacyAndNullPassThrough() {
        assertEquals("10.0.0.1", cipher.decrypt("10.0.0.1", CTX));
        assertNull(cipher.decrypt(null, CTX));
        assertNull(cipher.encrypt(null, CTX));
    }

    @Test
    @DisplayName("Chave errada não decifra; id de chave desconhecido falha com mensagem clara")
    void wrongOrUnknownKey() {
        String stored = cipher.encrypt("203.0.113.7", CTX);

        AesGcmCipher otherKeySameId = new AesGcmCipher(keys(KEY_V2, "v1", ""));
        assertThrows(DataDecryptionException.class, () -> otherKeySameId.decrypt(stored, CTX));

        AesGcmCipher onlyV2 = new AesGcmCipher(keys(KEY_V2, "v2", ""));
        DataDecryptionException e = assertThrows(DataDecryptionException.class, () -> onlyV2.decrypt(stored, CTX));
        assertTrue(e.getMessage().contains("DATA_ENCRYPTION_OLD_KEYS"));
    }

    @Test
    @DisplayName("Rotação: com a chave antiga configurada, decifra v1 e cifra novos valores com v2")
    void rotation() {
        String old = cipher.encrypt("203.0.113.7", CTX);
        AesGcmCipher rotated = new AesGcmCipher(keys(KEY_V2, "v2", "v1:" + KEY_V1));

        assertEquals("203.0.113.7", rotated.decrypt(old, CTX));
        assertTrue(rotated.encrypt("203.0.113.7", CTX).startsWith("enc:v2:"));
    }

    @Test
    @DisplayName("Valor truncado ou malformado é rejeitado")
    void malformed() {
        assertThrows(DataDecryptionException.class, () -> cipher.decrypt("enc:v1", CTX));
        assertThrows(DataDecryptionException.class, () -> cipher.decrypt("enc:v1:AAAA", CTX));
        assertThrows(DataDecryptionException.class, () -> cipher.decrypt("enc:v1:@@não-é-base64@@", CTX));
    }
}
