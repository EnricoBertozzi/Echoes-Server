package com.n0hana.echoes_server.institution;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
class InstitutionVerificationSchedulerTest {
    @Mock private InstitutionService institutionService;
    @InjectMocks private InstitutionVerificationScheduler scheduler;

    @Test
    void delegatesToService() {
        when(institutionService.retryPendingVerifications()).thenReturn(3);
        scheduler.retryPendingVerifications();
        verify(institutionService).retryPendingVerifications();
    }

    @Test
    void swallowsExceptions() {
        when(institutionService.retryPendingVerifications())
            .thenThrow(new RuntimeException("boom"));
        scheduler.retryPendingVerifications(); // não deve propagar
    }
}
