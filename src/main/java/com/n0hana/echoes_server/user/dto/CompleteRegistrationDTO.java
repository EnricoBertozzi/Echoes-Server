package com.n0hana.echoes_server.user.dto;

import java.util.List;

import com.n0hana.echoes_server.auth.password.PasswordByteLength;
import com.n0hana.echoes_server.auth.password.PasswordPolicy;
import com.n0hana.echoes_server.mfa.TwoFactorService;
import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.user.UserUtils;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompleteRegistrationDTO(
        @NotBlank(message = "O e-mail é obrigatório") @Email(message = "E-mail inválido")
        @Size(max = UserUtils.EMAIL_MAX_LENGTH) String email,

        @NotBlank(message = "A senha é obrigatória") @PasswordByteLength
        @Pattern(regexp = "^$|" + PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE) String password,

        @NotBlank(message = "O código é obrigatório")
        @Pattern(regexp = TwoFactorService.CODE_PATTERN, message = TwoFactorService.CODE_FORMAT_MESSAGE) String code,

        @NotNull(message = "A lista de termos aceitos é obrigatória") List<DocumentType> acceptedTerms) {
}
