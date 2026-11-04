package com.n0hana.echoes_server.auth.loginattempt;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço para proteção de rotas de login contra brute force
 * 
 * @author Enrico Bertozzi
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptService {
    private final static Integer ATTEMPTS_LIMIT = 5;
    private final static Duration BLOCK_DURATION = Duration.ofHours(1);
    private final LoginAttemptRepository attemptRepository;

    /**
     * Registra tentativas falhas de login e bloqueia o usuário quando o limite é
     * atingido
     * 
     * @param email E-mail do usuário a ser penalizado
     */
    public void registerFailedAttempt(String email) {
        User user = this.findUserByEmail(email);
        Integer attempts = user.getLoginAttempts();

        if (attempts < ATTEMPTS_LIMIT) {
            attempts += 1;
            attemptRepository.updateUserAttempts(user.getId(), attempts);
        } else {
            this.blockUser(user);
        }
    }

    /**
     * Reseta o registro de tentativas falhas de login quando o processo é concluído
     * corretamente
     * 
     * @param email E-mail do usuário a ser resetado
     */
    public void resetLoginAttempt(String email) {
        User user = this.findUserByEmail(email);
        attemptRepository.updateUserAttempts(user.getId(), 0);
    }

    /**
     * Busca usuário por seu e-mail cadastrado
     * 
     * @param email E-mail do usuário a ser procurado
     * 
     * @throws UserNotFoundException lançada quando o usuário não é encontrado
     * 
     * @return {@link User} encontrado
     */
    private User findUserByEmail(String email) {
        return attemptRepository.findUserByEmail(email).orElseThrow(UserNotFoundException::new);
    }

    /**
     * Bloqueia o usuário, por 1 hora, quando o limite de tentativas é atingida
     * 
     * @param user Usuário a ser bloqueado
     */
    private void blockUser(User user) {
        this.resetLoginAttempt(user.getEmail());
        attemptRepository.updateUserLockedUntil(user.getId(), LocalDateTime.now().plus(BLOCK_DURATION));
    }
}
