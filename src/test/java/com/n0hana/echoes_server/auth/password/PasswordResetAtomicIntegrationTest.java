package com.n0hana.echoes_server.auth.password;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.n0hana.echoes_server.auth.exception.AuthFailedException;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.User;

/** S1: uso único atômico do token de reset, contra um Redis real. */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIf("com.n0hana.echoes_server.support.RedisAvailability#redisReachable")
class PasswordResetAtomicIntegrationTest {

    @Autowired private PasswordService service;
    @Autowired private PasswordTokenRepository tokenRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder encoder;

    private String email;


    @org.junit.jupiter.api.BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    private void seedUser() {
        email = "reset-" + UUID.randomUUID() + "@test.local";
        Student user = new Student();
        user.setEmail(email);
        user.setName("Reset");
        user.setPassword(encoder.encode("SenhaAntiga123!"));
        userRepository.save(user);
    }

    @AfterEach
    void cleanup() {
        if (email != null) {
            tokenRepository.delete(email);
            userRepository.findByEmail(email).ifPresent(userRepository::delete);
        }
    }

    @Test
    @DisplayName("12 requisições simultâneas (acima do pool Hikari de 10): 1 vence, 11 falham, sem travar")
    void concurrentResetsSucceedExactlyOnce() throws Exception {
        seedUser();
        tokenRepository.save(email, "token-valido");

        int threads = 12; // acima do pool padrão do Hikari (10): não pode travar
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final String newPassword = "NovaSenha" + i + "!Aa";
            Callable<Void> task = () -> {
                RequestContextHolder.setRequestAttributes(
                        new ServletRequestAttributes(new MockHttpServletRequest()));
                ready.countDown();
                go.await();
                try {
                    service.reset(email, "token-valido", newPassword);
                    ok.incrementAndGet();
                } catch (AuthFailedException e) {
                    failed.incrementAndGet();
                } finally {
                    RequestContextHolder.resetRequestAttributes();
                }
                return null;
            };
            futures.add(pool.submit(task));
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        go.countDown();
        for (Future<?> f : futures) f.get(20, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(1, ok.get(), "exatamente um reset deve vencer");
        assertEquals(threads - 1, failed.get());
        assertTrue(tokenRepository.findByEmail(email).isEmpty());
        User user = userRepository.findByEmail(email).orElseThrow();
        assertNotNull(user.getPasswordChangedAt());
    }

    @Test
    @DisplayName("Token errado falha sem apagar o válido; token certo depois funciona")
    void wrongTokenDoesNotBurnValidToken() {
        seedUser();
        tokenRepository.save(email, "token-valido");

        assertThrows(AuthFailedException.class, () -> service.reset(email, "errado", "NovaSenha123!"));
        assertEquals("token-valido", tokenRepository.findByEmail(email).orElse(null));

        service.reset(email, "token-valido", "NovaSenha123!");
        assertTrue(tokenRepository.findByEmail(email).isEmpty());
        assertThrows(AuthFailedException.class, () -> service.reset(email, "token-valido", "OutraSenha123!"));
    }

    @Test
    @DisplayName("consumeIfMatches: ausente ou nulo retorna false")
    void missingOrNullToken() {
        seedUser();
        assertFalse(tokenRepository.consumeIfMatches(email, "qualquer"));
        tokenRepository.save(email, "t");
        assertFalse(tokenRepository.consumeIfMatches(email, null));
        assertTrue(tokenRepository.consumeIfMatches(email, "t"));
    }
}
