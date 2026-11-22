package com.n0hana.echoes_server.auth.password;

public final class PasswordPolicy {

    public static final String REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!]).{8,}$";

    public static final String MESSAGE = "Senha fraca";

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        return password != null && password.matches(REGEX);
    }
}
