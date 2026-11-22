package com.n0hana.echoes_server.infra.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import org.springframework.stereotype.Component;

/**
 * Criptografia de campo com AES-256-GCM (NIST SP 800-38D).
 *
 * <p>
 * Formato gravado: {@code enc:<idDaChave>:<Base64(IV ‖ cifrado ‖ tag)>}, com IV
 * aleatório de 96 bits por valor e tag de 128 bits. O {@code context} (ex.:
 * {@code "audit_log.ip"}) entra como AAD: um valor copiado de outra coluna não
 * decifra. Qualquer alteração no cifrado é detectada pela tag.
 * </p>
 *
 * <p>
 * Valores sem o prefixo {@code enc:} são devolvidos como estão, para que dados
 * gravados antes da criptografia continuem legíveis até serem migrados.
 * </p>
 */
@Component
public class AesGcmCipher {

    public static final String PREFIX = "enc:";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final DataEncryptionKeys keys;
    private final SecureRandom random = new SecureRandom();

    public AesGcmCipher(DataEncryptionKeys keys) {
        this.keys = keys;
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public String encrypt(String plaintext, String context) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keys.activeKey(), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(context));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] payload = ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array();
            return PREFIX + keys.activeKeyId() + ":" + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar valor de " + context, e);
        }
    }

    public String decrypt(String stored, String context) {
        if (stored == null || !isEncrypted(stored)) {
            return stored;
        }

        int separator = stored.indexOf(':', PREFIX.length());
        if (separator < 0) {
            throw new DataDecryptionException("Valor cifrado malformado em " + context);
        }
        String keyId = stored.substring(PREFIX.length(), separator);
        SecretKey key = keys.key(keyId)
            .orElseThrow(() -> new DataDecryptionException(
                "Chave '" + keyId + "' desconhecida ao decifrar " + context
                    + " (configure-a em DATA_ENCRYPTION_OLD_KEYS)"));

        try {
            byte[] payload = Base64.getDecoder().decode(stored.substring(separator + 1));
            if (payload.length < IV_BYTES + TAG_BITS / 8) {
                throw new DataDecryptionException("Valor cifrado truncado em " + context);
            }

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES));
            cipher.updateAAD(aad(context));
            byte[] plain = cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            // tag inválida: valor adulterado, chave errada ou contexto (coluna) trocado
            throw new DataDecryptionException("Não foi possível decifrar valor de " + context, e);
        }
    }

    private static byte[] aad(String context) {
        return context.getBytes(StandardCharsets.UTF_8);
    }
}
