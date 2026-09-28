package com.n0hana.echoes_server.institution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.n0hana.echoes_server.cnpj.CnpjDTO;
import com.n0hana.echoes_server.cnpj.CnpjService;
import com.n0hana.echoes_server.cnpj.exception.CnpjNaoEncontradoException;
import com.n0hana.echoes_server.cnpj.exception.CnpjProviderIndisponivelException;
import com.n0hana.echoes_server.institution.InstitutionVerificationService.VerificationOutcome;
import com.n0hana.echoes_server.notifier.InstitutionNotifier;

@ExtendWith(MockitoExtension.class)
class InstitutionVerificationServiceTest {

    @Mock private CnpjService cnpjService;
    @Mock private InstitutionRepository repository;
    @Mock private InstitutionNotifier notifier;

    @InjectMocks
    private InstitutionVerificationService service;

    private InstitutionModel pendingInstitution(UUID id) {
        return InstitutionModel.builder()
            .id(id)
            .name("Instituição Pendente")
            .acronym("IP")
            .cnpj("19131243000197")
            .email("pendente@teste.com")
            .verificationStatus(InstitutionVerificationStatus.PENDING_VERIFICATION)
            .build();
    }

    private CnpjDTO cnpjDTO() {
        return new CnpjDTO(
            "19131243000197",
            "RAZAO SOCIAL OFICIAL",
            "FANTASIA",
            "ATIVA",
            "Rua Y",
            "200",
            null,
            "Centro",
            "São Paulo",
            "SP",
            "01001000",
             null);
    }

    @Test
    @DisplayName("Sucesso → VERIFIED, dados aplicados, notifier.notifyVerificationCompleted")
    void successMarksVerified() {
        UUID id = UUID.randomUUID();
        InstitutionModel inst = pendingInstitution(id);

        when(repository.findById(id)).thenReturn(Optional.of(inst));
        when(cnpjService.consultar(inst.getCnpj())).thenReturn(cnpjDTO());
        when(repository.save(any(InstitutionModel.class))).thenAnswer(inv -> inv.getArgument(0));

        VerificationOutcome outcome = service.tryVerify(id);

        assertEquals(VerificationOutcome.VERIFIED, outcome);
        assertEquals(InstitutionVerificationStatus.VERIFIED, inst.getVerificationStatus());
        assertEquals("RAZAO SOCIAL OFICIAL", inst.getName());
        assertEquals("01001000", inst.getCep());
        verify(notifier).notifyVerificationCompleted(any());
        verify(notifier, never()).notifyVerificationRejected(any(), any());
    }

    @Test
    @DisplayName("Brasil API indisponível → STILL_PENDING, sem notificação, attempts incrementado")
    void providerUnavailableKeepsPending() {
        UUID id = UUID.randomUUID();
        InstitutionModel inst = pendingInstitution(id);
        int before = inst.getVerificationAttempts();

        when(repository.findById(id)).thenReturn(Optional.of(inst));
        when(cnpjService.consultar(inst.getCnpj()))
            .thenThrow(new CnpjProviderIndisponivelException("503"));
        when(repository.save(any(InstitutionModel.class))).thenAnswer(inv -> inv.getArgument(0));

        VerificationOutcome outcome = service.tryVerify(id);

        assertEquals(VerificationOutcome.STILL_PENDING, outcome);
        assertEquals(InstitutionVerificationStatus.PENDING_VERIFICATION, inst.getVerificationStatus());
        assertEquals(before + 1, inst.getVerificationAttempts());
        verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("CNPJ não encontrado → REJECTED + notifyVerificationRejected")
    void cnpjNotFoundMarksRejected() {
        UUID id = UUID.randomUUID();
        InstitutionModel inst = pendingInstitution(id);

        when(repository.findById(id)).thenReturn(Optional.of(inst));
        when(cnpjService.consultar(inst.getCnpj()))
            .thenThrow(new CnpjNaoEncontradoException("não encontrado"));
        when(repository.save(any(InstitutionModel.class))).thenAnswer(inv -> inv.getArgument(0));

        VerificationOutcome outcome = service.tryVerify(id);

        assertEquals(VerificationOutcome.REJECTED, outcome);
        assertEquals(InstitutionVerificationStatus.REJECTED, inst.getVerificationStatus());
        verify(notifier).notifyVerificationRejected(any(), any());
        verify(notifier, never()).notifyVerificationCompleted(any());
    }

    @Test
    @DisplayName("Instituição já VERIFIED → SKIPPED, sem consultar Brasil API")
    void verifiedSkips() {
        UUID id = UUID.randomUUID();
        InstitutionModel inst = pendingInstitution(id);
        inst.setVerificationStatus(InstitutionVerificationStatus.VERIFIED);

        when(repository.findById(id)).thenReturn(Optional.of(inst));

        VerificationOutcome outcome = service.tryVerify(id);

        assertEquals(VerificationOutcome.SKIPPED, outcome);
        verifyNoInteractions(cnpjService);
        verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("Instituição já REJECTED → SKIPPED, sem consultar Brasil API")
    void rejectedSkips() {
        UUID id = UUID.randomUUID();
        InstitutionModel inst = pendingInstitution(id);
        inst.setVerificationStatus(InstitutionVerificationStatus.REJECTED);

        when(repository.findById(id)).thenReturn(Optional.of(inst));

        VerificationOutcome outcome = service.tryVerify(id);

        assertEquals(VerificationOutcome.SKIPPED, outcome);
        verifyNoInteractions(cnpjService);
    }

    @Test
    @DisplayName("Instituição inexistente → SKIPPED")
    void notFoundSkips() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        VerificationOutcome outcome = service.tryVerify(id);

        assertEquals(VerificationOutcome.SKIPPED, outcome);
        verifyNoInteractions(cnpjService);
    }


    @Test
    @DisplayName("Situação cadastral BAIXADA → continua VERIFIED mas emite WARN")
    void situacaoBaixadaAindaMarcaVerified() {
        UUID id = UUID.randomUUID();
        InstitutionModel inst = pendingInstitution(id);

        CnpjDTO baixada = new CnpjDTO(
            "19131243000197", "RAZAO OFICIAL", "FANTASIA", "BAIXADA",
            "Rua Y", "200", null, "Centro", "São Paulo", "SP", "01001000", null);

        when(repository.findById(id)).thenReturn(Optional.of(inst));
        when(cnpjService.consultar(inst.getCnpj())).thenReturn(baixada);
        when(repository.save(any(InstitutionModel.class))).thenAnswer(inv -> inv.getArgument(0));

        VerificationOutcome outcome = service.tryVerify(id);

        assertEquals(VerificationOutcome.VERIFIED, outcome);
        assertEquals(InstitutionVerificationStatus.VERIFIED, inst.getVerificationStatus());
    }
}
