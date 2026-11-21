package com.n0hana.echoes_server.auth.exception;

/** A nova senha é igual à atual num fluxo que exige uma senha diferente (RF07). */
public class PasswordUnchangedException extends RuntimeException {

    public PasswordUnchangedException() {
        super("A nova senha deve ser diferente da senha atual");
    }
}
