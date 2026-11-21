package com.n0hana.echoes_server.infra.javers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.n0hana.echoes_server.infra.javers.monitor.JaversStorageAlertService;
import com.n0hana.echoes_server.infra.notifier.NotifierService;

class JaversStorageAlertServiceTests {

    @Test
    @DisplayName("O alerta é entregue ao notificador com os bytes e o limite")
    void alertsThroughNotifier() {
        NotifierService notifier = mock(NotifierService.class);

        new JaversStorageAlertService(notifier).alert("admin@test.local", 9L * 1024 * 1024 * 1024, 8192L);

        verify(notifier).sendJaversStorageAlert("admin@test.local", 9L * 1024 * 1024 * 1024, 8192L);
    }
}
