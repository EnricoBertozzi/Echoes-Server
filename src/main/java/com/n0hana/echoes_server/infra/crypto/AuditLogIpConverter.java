package com.n0hana.echoes_server.infra.crypto;

import jakarta.persistence.Converter;

/**
 * Cifra o IP da trilha de auditoria ({@code audit_log.ip}).
 *
 * <p>
 * A assinatura HMAC do registro é calculada sobre o IP em claro (valor da
 * entidade), então a verificação de integridade continua valendo. Registros
 * gravados antes da criptografia ficam em claro: os gatilhos do {@code audit_log}
 * impedem {@code UPDATE}, e eles saem pelo expurgo de retenção.
 * </p>
 */
@Converter
public class AuditLogIpConverter extends EncryptedStringConverter {

    public static final String CONTEXT = "audit_log.ip";

    public AuditLogIpConverter(AesGcmCipher cipher) {
        super(cipher, CONTEXT);
    }
}
