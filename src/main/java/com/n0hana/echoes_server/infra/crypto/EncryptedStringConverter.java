package com.n0hana.echoes_server.infra.crypto;

import jakarta.persistence.AttributeConverter;

/**
 * Base dos converters JPA que cifram uma coluna de texto com {@link AesGcmCipher}.
 *
 * <p>
 * Cada subclasse fixa o contexto (tabela.coluna), usado como AAD. O Hibernate obtém
 * os converters pelo container de beans do Spring (configurado pelo Spring Boot),
 * por isso o cifrador chega pelo construtor.
 * </p>
 */
public abstract class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final AesGcmCipher cipher;
    private final String context;

    protected EncryptedStringConverter(AesGcmCipher cipher, String context) {
        this.cipher = cipher;
        this.context = context;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cipher.encrypt(attribute, context);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return cipher.decrypt(dbData, context);
    }

    public String context() {
        return context;
    }
}
