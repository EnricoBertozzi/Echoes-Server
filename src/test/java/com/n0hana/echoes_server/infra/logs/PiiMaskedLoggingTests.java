package com.n0hana.echoes_server.infra.logs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.condition.EnabledIf;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@EnabledIf("com.n0hana.echoes_server.support.RedisAvailability#redisReachable")
class PiiMaskedLoggingTests {

    private static final Logger log = LoggerFactory.getLogger("com.n0hana.echoes_server.teste.pii");

    @Test
    @DisplayName("Mensagem de log com e-mail e token sai mascarada")
    void messageIsMasked(CapturedOutput output) {
        log.warn("Falha para {} com token=segredo123", "maria.silva@escola.edu.br");

        assertTrue(output.getOut().contains("Falha para " + PiiMasker.EMAIL), output.getOut());
        assertFalse(output.getOut().contains("maria.silva"));
        assertFalse(output.getOut().contains("segredo123"));
    }

    @Test
    @DisplayName("Mensagem da exceção e stack trace também saem mascarados")
    void exceptionIsMasked(CapturedOutput output) {
        log.error("Erro inesperado", new IllegalStateException("conta joao.pereira@escola.edu.br bloqueada"));

        assertTrue(output.getOut().contains("IllegalStateException"), output.getOut());
        assertFalse(output.getOut().contains("joao.pereira"), output.getOut());
    }
}
