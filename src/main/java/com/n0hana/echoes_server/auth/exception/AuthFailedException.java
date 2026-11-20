package com.n0hana.echoes_server.auth.exception;

public class AuthFailedException extends RuntimeException {
    public AuthFailedException() {
        super("Erro ao autenticar");
    }

    /**
     * Permite subclasses com mensagem própria. Subclasses herdam a semântica de
     * "falha de autenticação", logo o {@code dontRollbackOn} dos fluxos de login
     * também as cobre (B1).
     */
    protected AuthFailedException(String message) {
        super(message);
    }
}
