package com.n0hana.echoes_server.auth.exception;

/**
 * Exceção para o erro de código de autenticação multifator.
 *
 * <p>
 * Estende {@link AuthFailedException} para que o {@code dontRollbackOn} de
 * {@code AuthService.verifyMfaCode} a preserve: sem isso a transação sofria
 * rollback e a falha não era contada (B1).
 * </p>
 *
 * @author Enrico Bertozzi
 */
public class InvalidMfaCodeException extends AuthFailedException {

    public InvalidMfaCodeException() {
        super("Código Inválido");
    }
}
