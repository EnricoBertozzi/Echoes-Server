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
