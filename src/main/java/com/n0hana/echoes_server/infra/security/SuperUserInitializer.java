package com.n0hana.echoes_server.infra.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.auth.password.PasswordPolicy;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Classe de configuração para inicialização do super usuário
 */
@Slf4j
@Configuration
@Profile("!test")
@RequiredArgsConstructor
public class SuperUserInitializer {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Value("${api.security.admin.email}")
    private String adminEmail;

    @Value("${api.security.admin.username}")
    private String adminUsername;

    @Value("${api.security.admin.password}")
    private String adminPassword;

    @Bean
    ApplicationRunner initializeSuperUser() {
        return (args) -> {
            if (!repository.existsByEmail(adminEmail)) {
                // o seed só usa a senha ao criar o admin; senha fraca aborta a subida (S1/RNF07)
                if (!PasswordPolicy.isValid(adminPassword)) {
                    throw new IllegalStateException(
                            "ADMIN_PASSWORD não atende à política de senha: " + PasswordPolicy.MESSAGE);
                }

                User user = new Admin();
                user.setName(adminUsername);
                user.setEmail(adminEmail);
                user.setPassword(passwordEncoder.encode(adminPassword));
                user.setLoginAttempts(0);
                user.setLockUntil(null);
                user.setActive(true);

                repository.save(user);
                log.info("SuperUser inicializado: {}", adminEmail);
            }
        };
    }
}
