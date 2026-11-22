package com.n0hana.echoes_server.infra.crypto;

import jakarta.persistence.Converter;

/** Cifra o IP do aceite de termos ({@code user_terms_acceptance.ip_address}). */
@Converter
public class AcceptanceIpConverter extends EncryptedStringConverter {

    public static final String CONTEXT = "user_terms_acceptance.ip_address";

    public AcceptanceIpConverter(AesGcmCipher cipher) {
        super(cipher, CONTEXT);
    }
}
