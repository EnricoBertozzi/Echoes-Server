package com.n0hana.echoes_server.infra.javers.monitor;

import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.infra.notifier.NotifierService;

import lombok.RequiredArgsConstructor;

/** Bean separado para o alerta passar pelo proxy e ser auditado. */
@Service
@RequiredArgsConstructor
public class JaversStorageAlertService {

    private final NotifierService notifier;

    @Auditable(action = "JAVERS_STORAGE_ALERT", entity = "Javers", details = "'bytes=' + #bytes")
    public void alert(String to, long bytes, long thresholdMb) {
        notifier.sendJaversStorageAlert(to, bytes, thresholdMb);
    }
}
