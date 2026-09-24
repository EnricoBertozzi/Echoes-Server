package com.n0hana.echoes_server.institution;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduler que retenta verificar instituições pendentes.
 *
 * <p>
 * Roda a cada {@code institution.verification.retry-interval-ms} (padrão: 30min).
 * {@code initialDelay} de 1min evita execução antes de a aplicação terminar de subir.
 * Usa {@code fixedDelay} (não {@code fixedRate}) para impedir sobreposição caso
 * um ciclo demore mais que o intervalo.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InstitutionVerificationScheduler {

    private final InstitutionService institutionService;

    @Scheduled(
        fixedDelayString = "${institution.verification.retry-interval-ms:5000}",
        initialDelayString = "${institution.verification.initial-delay-ms:5000}"
    )
    public void retryPendingVerifications() {
        try {
            int processed = institutionService.retryPendingVerifications();
            if (processed > 0) {
                log.info("Scheduler de verificação: {} instituições verificadas com sucesso", processed);
            }
        } catch (Exception e) {
            log.error("Erro inesperado no scheduler de verificação de instituições", e);
        }
    }
}
