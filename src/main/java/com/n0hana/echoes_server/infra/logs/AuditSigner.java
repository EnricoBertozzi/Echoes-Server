package com.n0hana.echoes_server.infra.logs;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class AuditSigner {

    private static final int MIN_KEY_BYTES = 32;
    private static final String SEP = "\u001F";

    @Value("${api.audit.hmac-key:}")
    private String key;

    @PostConstruct
    void validateKey() {
        if (key == null || key.getBytes(StandardCharsets.UTF_8).length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                "AUDIT_HMAC_KEY precisa ter pelo menos " + MIN_KEY_BYTES + " bytes");
        }
    }

    public String sign(AuditLog log) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(canonical(log).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao assinar registro de auditoria", e);
        }
    }

    public boolean verify(AuditLog log) {
        if (log.getSignature() == null)
            return false;
        return MessageDigest.isEqual(
                sign(log).getBytes(StandardCharsets.UTF_8),
                log.getSignature().getBytes(StandardCharsets.UTF_8));
    }

    /** campos na ordem fixa; timestamp em segundos, pois o banco pode truncar a fração */
    private static String canonical(AuditLog log) {
        Instant ts = log.getTimestamp();
        String seconds = ts == null ? "" : String.valueOf(ts.truncatedTo(ChronoUnit.SECONDS).getEpochSecond());
        return String.join(SEP,
                "v2",
                nz(log.getUserId()), nz(log.getAction()), nz(log.getEntity()), nz(log.getStatus()),
                nz(log.getDetails()), nz(log.getIp()), nz(log.getEntityId()), nz(log.getInstitutionId()),
                nz(log.getEntityName()), nz(log.getActorName()), nz(log.getActorRole()),
                nz(log.getActorInstitutionName()), seconds);
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
