package com.n0hana.echoes_server.institution.notifier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@ConditionalOnProperty(name = "institution.notifier", havingValue = "logger")
public class LoggerInstitutionNotifier implements InstitutionNotifier {

    @Override
    public void notifyPendingVerification(InstitutionNotificationData data, String reason) {
        log.info("[PENDING_VERIFICATION] Instituição {} (CNPJ {}) — {}", data.name(), data.cnpj(), reason);
    }

    @Override
    public void notifyVerificationCompleted(InstitutionNotificationData data) {
        log.info("[VERIFIED] Instituição {} (CNPJ {}) verificada com sucesso.", data.name(), data.cnpj());
    }

    @Override
    public void notifyVerificationRejected(InstitutionNotificationData data, String reason) {
        log.info("[REJECTED] Instituição {} (CNPJ {}) — {}", data.name(), data.cnpj(), reason);
    }
}
