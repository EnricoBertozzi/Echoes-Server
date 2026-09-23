package com.n0hana.echoes_server.auth.password;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Modelo para armazenamento de código multifator no banco em memória
 * 
 * @author Enrico Bertozzi
 * @since 0.1.1
 */
@Getter
@Setter
@NoArgsConstructor
public class PasswordCodeModel {
    private String code;
    private boolean isValidated;

    public PasswordCodeModel(String code) {
        this.code = code;
        this.isValidated = false;
    }
}
