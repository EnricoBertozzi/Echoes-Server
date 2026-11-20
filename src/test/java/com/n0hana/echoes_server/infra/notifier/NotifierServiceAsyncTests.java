package com.n0hana.echoes_server.infra.notifier;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.n0hana.echoes_server.infra.AsyncConfig;

@SpringJUnitConfig(classes = { AsyncConfig.class, NotifierServiceAsyncTests.Config.class })
class NotifierServiceAsyncTests {

    @Configuration
    static class Config {
        @Bean
        EmailNotifier emailNotifier() {
            return mock(EmailNotifier.class);
        }

        @Bean
        NotifierService notifierService(EmailNotifier emailNotifier) {
            return new NotifierService(emailNotifier);
        }
    }

    @Autowired private NotifierService service;
    @Autowired private EmailNotifier emailNotifier;

    @Test
    @DisplayName("Com SMTP lento (300 ms) a chamada retorna em menos de 50 ms e o envio acontece depois, em thread própria")
    void returnsImmediatelyAndSendsLater() {
        AtomicReference<String> thread = new AtomicReference<>();
        doAnswer(invocation -> {
            thread.set(Thread.currentThread().getName());
            Thread.sleep(300);
            return null;
        }).when(emailNotifier).send(eq("a@b.com"), any());

        long start = System.nanoTime();
        service.sendMfaCode("a@b.com", "123456");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMs < 50, "chamada bloqueou por " + elapsedMs + " ms");
        verify(emailNotifier, timeout(2000)).send(eq("a@b.com"), any());
        assertTrue(thread.get().startsWith("echoes-async-"), "executou em " + thread.get());
    }
}
