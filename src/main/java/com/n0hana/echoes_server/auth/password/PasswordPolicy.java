package com.n0hana.echoes_server.auth.password;

/**
 * Política única de complexidade de senhas (RNF07).
 *
 * <p>
 * Usada pelos DTOs de cadastro, reset e troca de senha via
 * {@code @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)}
 * e pelo seed do administrador, para que a regra não fique duplicada.
 * </p>
 */
public final class PasswordPolicy {

    public static final String REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!]).{8,}$";

    public static final String MESSAGE = "Senha fraca";

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        return password != null && password.matches(REGEX);
    }
}
