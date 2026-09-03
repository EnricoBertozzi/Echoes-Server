package com.n0hana.echoes_server.service.ratelimit;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.model.User;
import com.n0hana.echoes_server.repository.UserRepository;
import com.n0hana.echoes_server.service.logs.Auditable;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {
    
    private final UserRepository userRepository;

    public final int MAX_ATTEMPTS = 5;
    private final Duration LOCK_TIME = Duration.ofMinutes(30);

    @Auditable(action = "Falha na Tentativa de Login", entity = "LOGIN")
    public int loginFailed(String email) {

        User user = userRepository.findUserByEmail(email).orElse(null);
        if (user == null) return 0;
        int attempts = user.getLoginAttempts() + 1;
        user.setLoginAttempts(attempts);
        if(attempts >= MAX_ATTEMPTS){
            user.setLockUntil(LocalDateTime.now().plus(LOCK_TIME));
        }
        userRepository.save(user);
        return attempts;
    }

    @Auditable(action = "Login Bem Sucedido", entity = "LOGIN")
    public void loginSucceeded(String email) {
        User user = userRepository.findUserByEmail(email).orElse(null);
        if(user == null) return;
        user.setLoginAttempts(0);
        user.setLockUntil(null);
        userRepository.save(user);
    }
}
