package com.n0hana.echoes_server.infra.javers;

import org.javers.spring.auditable.AuthorProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;

import com.n0hana.echoes_server.user.model.User;

@Configuration
public class JaversConfig {

    // quem fez a alteração, usa o id do usuário logado
    // sem login, como no scheduler, grava sistema
    @Bean
    public AuthorProvider authorProvider() {
        return () -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth != null && auth.getPrincipal() instanceof User user) {
                return user.getId().toString();
            }
            return "sistema";
        };
    }
}
