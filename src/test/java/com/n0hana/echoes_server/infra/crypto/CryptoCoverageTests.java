package com.n0hana.echoes_server.infra.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Criptografia de campo: chaves, cifrador e converters")
class CryptoCoverageTests {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static String randomKey() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }

    private String keyA;
    private String keyB;
    private DataEncryptionKeys keys;
    private AesGcmCipher cipher;

    @BeforeEach
    void setUp() {
        keyA = randomKey();
        keyB = randomKey();
        keys = new DataEncryptionKeys(keyA, "v1", "", randomKey(), randomKey());
        cipher = new AesGcmCipher(keys);
    }

    // ---------- DataEncryptionKeys ----------

    @Test
    @DisplayName("expõe a chave ativa, o id e a consulta por id")
    void accessors() {
        DataEncryptionKeys k = new DataEncryptionKeys(keyA, " v2 ", "old1:" + keyB, null, null);

        assertEquals("v2", k.activeKeyId());
        assertEquals(32, k.activeKey().getEncoded().length);
        assertTrue(k.key("v2").isPresent());
        assertTrue(k.key("old1").isPresent());
        assertTrue(k.key("zzz").isEmpty());
    }

    @Test
    @DisplayName("chave nula é recusada")
    void chaveNula() {
        assertThrows(IllegalStateException.class, () -> new DataEncryptionKeys(null, "v1", "", "", ""));
    }

    @Test
    @DisplayName("id de chave nulo é inválido")
    void idNulo() {
        assertThrows(IllegalStateException.class, () -> new DataEncryptionKeys(keyA, null, "", "", ""));
    }

    @Test
    @DisplayName("chave igual ao segredo do JWT ou ao HMAC de auditoria é recusada (ignorando espaços)")
    void igualAOutrosSegredos() {
        assertThrows(IllegalStateException.class, () -> new DataEncryptionKeys(keyA, "v1", "", " " + keyA + " ", ""));
        assertThrows(IllegalStateException.class, () -> new DataEncryptionKeys(keyA, "v1", "", "", keyA));
    }

    @Test
    @DisplayName("chaves antigas: formato inválido, id repetido e Base64/tamanho inválidos")
    void chavesAntigasInvalidas() {
        assertThrows(IllegalStateException.class, () -> new DataEncryptionKeys(keyA, "v1", "semdoispontos", "", ""));
        assertThrows(IllegalStateException.class, () -> new DataEncryptionKeys(keyA, "v1", "v1:" + keyB, "", ""));
        assertThrows(IllegalStateException.class, () -> new DataEncryptionKeys(keyA, "v1", "a:" + keyB + ",a:" + keyA, "", ""));
        assertThrows(IllegalStateException.class, () -> new DataEncryptionKeys(keyA, "v1", "a:%%%", "", ""));
        assertThrows(IllegalStateException.class, () -> new DataEncryptionKeys(keyA, "v1", "a:"
                + Base64.getEncoder().encodeToString(new byte[16]), "", ""));
        assertThrows(IllegalStateException.class, () -> new DataEncryptionKeys(keyA, "v1", "id-invalido!:" + keyB, "", ""));
    }

    @Test
    @DisplayName("aceita várias chaves antigas separadas por vírgula")
    void variasChavesAntigas() {
        DataEncryptionKeys k = new DataEncryptionKeys(keyA, "v3", "v1:" + keyB + ", v2:" + randomKey(), "", "");

        assertTrue(k.key("v1").isPresent());
        assertTrue(k.key("v2").isPresent());
    }

    // ---------- AesGcmCipher ----------

    @Test
    @DisplayName("cifra e decifra, com IV aleatório a cada chamada")
    void roundTrip() {
        String a = cipher.encrypt("198.51.100.7", "audit_log.ip");
        String b = cipher.encrypt("198.51.100.7", "audit_log.ip");

        assertTrue(a.startsWith("enc:v1:"));
        assertNotEquals(a, b);
        assertEquals("198.51.100.7", cipher.decrypt(a, "audit_log.ip"));
        assertEquals("198.51.100.7", cipher.decrypt(b, "audit_log.ip"));
    }

    @Test
    @DisplayName("nulo passa direto e valores sem prefixo são devolvidos como estão")
    void nuloELegado() {
        assertNull(cipher.encrypt(null, "ctx"));
        assertNull(cipher.decrypt(null, "ctx"));
        assertEquals("198.51.100.7", cipher.decrypt("198.51.100.7", "ctx"));
        assertTrue(AesGcmCipher.isEncrypted("enc:x"));
        assertFalse(AesGcmCipher.isEncrypted("x"));
        assertFalse(AesGcmCipher.isEncrypted(null));
    }

    @Test
    @DisplayName("valor sem o separador do id da chave é malformado")
    void malformado() {
        assertThrows(DataDecryptionException.class, () -> cipher.decrypt("enc:semseparador", "ctx"));
    }

    @Test
    @DisplayName("id de chave desconhecido é recusado")
    void chaveDesconhecida() {
        DataEncryptionKeys outras = new DataEncryptionKeys(keyB, "v9", "", "", "");
        String stored = new AesGcmCipher(outras).encrypt("x", "ctx");

        DataDecryptionException e = assertThrows(DataDecryptionException.class, () -> cipher.decrypt(stored, "ctx"));
        assertTrue(e.getMessage().contains("v9"));
    }

    @Test
    @DisplayName("payload truncado é recusado")
    void truncado() {
        String curto = "enc:v1:" + Base64.getEncoder().encodeToString(new byte[10]);

        assertThrows(DataDecryptionException.class, () -> cipher.decrypt(curto, "ctx"));
    }

    @Test
    @DisplayName("Base64 inválido é recusado")
    void base64Invalido() {
        assertThrows(DataDecryptionException.class, () -> cipher.decrypt("enc:v1:@@@@", "ctx"));
    }

    @Test
    @DisplayName("contexto trocado ou conteúdo adulterado é detectado pela tag")
    void adulteracao() {
        String stored = cipher.encrypt("segredo", "tabela.a");

        assertThrows(DataDecryptionException.class, () -> cipher.decrypt(stored, "tabela.b"));

        byte[] payload = Base64.getDecoder().decode(stored.substring("enc:v1:".length()));
        payload[payload.length - 1] ^= 0x01;
        String adulterado = "enc:v1:" + Base64.getEncoder().encodeToString(payload);
        assertThrows(DataDecryptionException.class, () -> cipher.decrypt(adulterado, "tabela.a"));
    }

    @Test
    @DisplayName("decifra valores gravados com uma chave antiga")
    void rotacaoDeChave() {
        String antigo = cipher.encrypt("valor", "ctx");
        DataEncryptionKeys rotacionadas = new DataEncryptionKeys(keyB, "v2", "v1:" + keyA, "", "");

        assertEquals("valor", new AesGcmCipher(rotacionadas).decrypt(antigo, "ctx"));
    }

    // ---------- Converters ----------

    @Test
    @DisplayName("conversores de IP usam o contexto da coluna e fazem round-trip")
    void conversores() {
        AuditLogIpConverter audit = new AuditLogIpConverter(cipher);
        AcceptanceIpConverter aceite = new AcceptanceIpConverter(cipher);

        assertEquals("audit_log.ip", audit.context());
        assertEquals("user_terms_acceptance.ip_address", aceite.context());

        String db = audit.convertToDatabaseColumn("192.0.2.10");
        assertTrue(AesGcmCipher.isEncrypted(db));
        assertEquals("192.0.2.10", audit.convertToEntityAttribute(db));
        // um valor de outra coluna não decifra
        assertThrows(DataDecryptionException.class, () -> aceite.convertToEntityAttribute(db));
        assertNull(audit.convertToDatabaseColumn(null));
        assertNull(audit.convertToEntityAttribute(null));
        assertEquals("legado", audit.convertToEntityAttribute("legado"));
        assertSame(audit.context(), audit.context());
    }
}
