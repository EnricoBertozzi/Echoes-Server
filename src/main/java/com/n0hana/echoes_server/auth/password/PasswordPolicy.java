package com.n0hana.echoes_server.auth.password;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {

    public static final int MAX_EXISTING_PASSWORD_LENGTH = 1024;
    public static final int MAX_NEW_PASSWORD_BYTES = 72;
    public static final String REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!]).{8,}$";

    public static final String MESSAGE = "Senha fraca";

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        return password != null && fitsBcrypt(password) && password.matches(REGEX);
    }

    public static boolean fitsBcrypt(String password) {
        return password.length() <= MAX_NEW_PASSWORD_BYTES
            && password.getBytes(StandardCharsets.UTF_8).length <= MAX_NEW_PASSWORD_BYTES;
    }
}
