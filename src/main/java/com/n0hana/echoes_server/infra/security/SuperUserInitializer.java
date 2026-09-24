package com.n0hana.echoes_server.infra.security;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.UserService;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.User;

import jakarta.persistence.Column;

/**
 * Classe de configuração para inicialização do super usuário
 */
@Configuration 
public class SuperUserInitializer {
    
    @Autowired 
    private UserRepository repository;

    @Value("${api.security.admin.email}")
    private String adminEmail;

    @Value("${api.security.admin.username}")
    private String adminUsername;

    @Value("${api.security.admin.password}")
    private String adminPassword;

    @Autowired 
    private PasswordEncoder passwordEncoder;

    @Bean
    ApplicationRunner initializeSuperUser() {
        return (args) -> {
            if (!repository.existsByEmail(adminEmail)) {
                User user = new Admin();
                user.setName(adminUsername);
                user.setEmail(adminEmail);
                user.setPassword(passwordEncoder.encode(adminPassword));
                user.setLoginAttempts(0);
                user.setLockUntil(null);
                user.setActive(true);
    
                repository.save(user);
            }
        };
    }
}
