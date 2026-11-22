package com.n0hana.echoes_server.infra.crypto;

/** Valor cifrado adulterado, malformado ou gravado com uma chave que não está configurada. */
public class DataDecryptionException extends RuntimeException {

    public DataDecryptionException(String message) {
        super(message);
    }

    public DataDecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
