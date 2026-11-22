package com.n0hana.echoes_server.infra.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DataEncryptionKeys {

    static final int KEY_BYTES = 32;
    static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9]{1,16}");
    static final String ALGORITHM = "AES";

    private final String activeKeyId;
    private final Map<String, SecretKey> keys;

    public DataEncryptionKeys(
            @Value("${api.crypto.data-key:}") String dataKey,
            @Value("${api.crypto.key-id:v1}") String keyId,
            @Value("${api.crypto.old-keys:}") String oldKeys,
            @Value("${api.security.token.secret:}") String jwtSecret,
            @Value("${api.audit.hmac-key:}") String auditHmacKey) {

        if (dataKey == null || dataKey.isBlank()) {
            throw new IllegalStateException(
                "DATA_ENCRYPTION_KEY é obrigatória (Base64 de 32 bytes, ex.: openssl rand -base64 32)");
        }
        if (sameSecret(dataKey, jwtSecret) || sameSecret(dataKey, auditHmacKey)) {
            throw new IllegalStateException(
                "DATA_ENCRYPTION_KEY deve ser diferente de JWT_SECRET e de AUDIT_HMAC_KEY");
        }

        Map<String, SecretKey> parsed = new LinkedHashMap<>();
        this.activeKeyId = validKeyId(keyId, "DATA_ENCRYPTION_KEY_ID");
        parsed.put(activeKeyId, decode(dataKey, "DATA_ENCRYPTION_KEY"));

        if (oldKeys != null && !oldKeys.isBlank()) {
            for (String entry : oldKeys.split(",")) {
                String[] parts = entry.trim().split(":", 2);
                if (parts.length != 2) {
                    throw new IllegalStateException("DATA_ENCRYPTION_OLD_KEYS deve ter o formato id:base64[,id:base64]");
                }
                String id = validKeyId(parts[0], "DATA_ENCRYPTION_OLD_KEYS");
                if (parsed.containsKey(id)) {
                    throw new IllegalStateException("Id de chave repetido em DATA_ENCRYPTION_OLD_KEYS: " + id);
                }
                parsed.put(id, decode(parts[1], "DATA_ENCRYPTION_OLD_KEYS (" + id + ")"));
            }
        }

        this.keys = Collections.unmodifiableMap(parsed);
    }

    /** id da chave usada para cifrar valores novos */
    public String activeKeyId() {
        return activeKeyId;
    }

    public SecretKey activeKey() {
        return keys.get(activeKeyId);
    }

    /** chave para decifrar um valor gravado com {@code keyId} (ativa ou antiga) */
    public Optional<SecretKey> key(String keyId) {
        return Optional.ofNullable(keys.get(keyId));
    }

    private static String validKeyId(String keyId, String setting) {
        String id = keyId == null ? "" : keyId.trim();
        if (!KEY_ID.matcher(id).matches()) {
            throw new IllegalStateException(setting + ": id de chave inválido (1 a 16 letras ou números)");
        }
        return id;
    }

    private static SecretKey decode(String base64, String setting) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(setting + " não é Base64 válido");
        }
        if (raw.length != KEY_BYTES) {
            throw new IllegalStateException(
                setting + " precisa ter exatamente " + KEY_BYTES + " bytes (AES-256); tem " + raw.length);
        }
        return new SecretKeySpec(raw, ALGORITHM);
    }

    private static boolean sameSecret(String a, String b) {
        if (b == null || b.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(a.trim().getBytes(StandardCharsets.UTF_8), b.trim().getBytes(StandardCharsets.UTF_8));
    }
}
