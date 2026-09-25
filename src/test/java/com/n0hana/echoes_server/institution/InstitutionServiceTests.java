package com.n0hana.echoes_server.institution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.n0hana.echoes_server.cnpj.CnpjDTO;
import com.n0hana.echoes_server.cnpj.CnpjService;
import com.n0hana.echoes_server.cnpj.exception.CnpjProviderIndisponivelException;
import com.n0hana.echoes_server.institution.exception.InstitutionNotFoundException;
import com.n0hana.echoes_server.institution.exception.InstitutionPendingVerificationException;
import com.n0hana.echoes_server.notifier.InstitutionNotifier;

@ExtendWith(MockitoExtension.class)
public class InstitutionServiceTests {

    @Mock
    private InstitutionRepository repository;
    @Mock
    private CnpjService cnpjService;
    @Mock
    private InstitutionVerificationService verificationService;
    @Mock
    private InstitutionNotifier notifier;

    @InjectMocks
    private InstitutionService service;

    private InstitutionModel createTestModel(UUID id) {
        return InstitutionModel.builder()
                .id(id)
                .name("Instituição Teste")
                .acronym("IT")
                .cnpj("12.345.678/0001-90")
                .email("contato@teste.com")
                .phone("11999999999")
                .address("Rua Teste, 123")
                .active(true)
                .deleted(false)
                .build();
    }

    private CnpjDTO cnpjDTO() {
        return new CnpjDTO(
                "12345678000190", // cnpj
                "Instituição Teste LTDA", // razaoSocial
                "IT", // nome nomeFantasia
                "ATIVA", // situacao cadastral
                "Rua Teste", // logradouro
                "123", // numero
                null, // complemento
                "Centro", // bairro
                "São Paulo", // municipio
                "SP", // uf
                "01001000", // cep
                null); // telefone
    }

    @Test
    @DisplayName("Create com Brasil API OK → VERIFIED, dados aplicados, sem notificação")
    void shouldCreateSuccessfulInstitution() {
        InstitutionModel model = this.createTestModel(null);
        String expectedCnpj = "12345678000190";

        when(repository.existsByCnpjOrEmail(expectedCnpj, model.getEmail())).thenReturn(false);
        when(cnpjService.consultar(expectedCnpj)).thenReturn(cnpjDTO());
        when(repository.save(any(InstitutionModel.class))).thenAnswer(inv -> inv.getArgument(0));

        InstitutionModel result = service.create(model);

        assertNotNull(result);
        assertEquals(expectedCnpj, result.getCnpj());
        assertEquals(InstitutionVerificationStatus.VERIFIED, result.getVerificationStatus());
        verify(verificationService).applyDadosReceita(eq(model), any(CnpjDTO.class));
        verify(notifier, never()).notifyPendingVerification(any(), any());
    }

    @Test
    @DisplayName("Create com Brasil API indisponível → PENDING_VERIFICATION + notificação + persistência")
    void shouldMarkAsPendingWhenBrasilApiUnavailable() {
        InstitutionModel model = this.createTestModel(null);
        String expectedCnpj = "12345678000190";

        when(repository.existsByCnpjOrEmail(expectedCnpj, model.getEmail())).thenReturn(false);
        when(cnpjService.consultar(expectedCnpj))
                .thenThrow(new CnpjProviderIndisponivelException("503"));
        when(repository.save(any(InstitutionModel.class))).thenAnswer(inv -> inv.getArgument(0));

        InstitutionModel result = service.create(model);

        assertEquals(InstitutionVerificationStatus.PENDING_VERIFICATION, result.getVerificationStatus());
        verify(notifier).notifyPendingVerification(any(), any());
        verify(verificationService, never()).applyDadosReceita(any(), any());
    }

    @Test
    @DisplayName("Create com CNPJ já cadastrado → DuplicateKeyException sem consultar Brasil API")
    void shouldThrowDuplicationKeyException() {
        InstitutionModel model = this.createTestModel(null);
        String expectedCnpj = "12345678000190";

        when(repository.existsByCnpjOrEmail(expectedCnpj, model.getEmail())).thenReturn(true);

        assertThrows(DuplicateKeyException.class, () -> service.create(model));
        verify(repository, never()).save(any());
        verify(cnpjService, never()).consultar(any());
    }

    @Test
    @DisplayName("assertCanPurchase liberado para VERIFIED")
    void assertCanPurchaseAllowsVerified() {
        UUID id = UUID.randomUUID();
        InstitutionModel model = createTestModel(id);
        model.setVerificationStatus(InstitutionVerificationStatus.VERIFIED);

        when(repository.findById(id)).thenReturn(Optional.of(model));

        service.assertCanPurchase(id);
        // sem throw = sucesso
    }

    @Test
    @DisplayName("assertCanPurchase bloqueado para PENDING_VERIFICATION")
    void assertCanPurchaseBlocksPending() {
        UUID id = UUID.randomUUID();
        InstitutionModel model = createTestModel(id);
        model.setVerificationStatus(InstitutionVerificationStatus.PENDING_VERIFICATION);

        when(repository.findById(id)).thenReturn(Optional.of(model));

        assertThrows(InstitutionPendingVerificationException.class,
                () -> service.assertCanPurchase(id));
    }

    @Test
    @DisplayName("assertCanPurchase bloqueado para REJECTED")
    void assertCanPurchaseBlocksRejected() {
        UUID id = UUID.randomUUID();
        InstitutionModel model = createTestModel(id);
        model.setVerificationStatus(InstitutionVerificationStatus.REJECTED);

        when(repository.findById(id)).thenReturn(Optional.of(model));

        assertThrows(InstitutionPendingVerificationException.class,
                () -> service.assertCanPurchase(id));
    }

    @Test
    @DisplayName("assertCanPurchase em instituição inexistente → InstitutionNotFoundException")
    void assertCanPurchaseThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(InstitutionNotFoundException.class, () -> service.assertCanPurchase(id));
    }

    @Test
    @DisplayName("retryPendingVerifications conta apenas VERIFIED como sucesso")
    void retryPendingVerificationsCountsOnlyVerified() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        InstitutionModel m1 = createTestModel(id1);
        InstitutionModel m2 = createTestModel(id2);

        when(repository.findAllByVerificationStatus(InstitutionVerificationStatus.PENDING_VERIFICATION))
                .thenReturn(List.of(m1, m2));
        when(verificationService.tryVerify(id1))
                .thenReturn(InstitutionVerificationService.VerificationOutcome.VERIFIED);
        when(verificationService.tryVerify(id2))
                .thenReturn(InstitutionVerificationService.VerificationOutcome.STILL_PENDING);

        int success = service.retryPendingVerifications();

        assertEquals(1, success);
    }

    @Test
    @DisplayName("Deve filtrar as instituições cadastradas por nome quando o parâmetro é fornecido")
    void shouldFilterInstitutionsByNameWhenParameterIsProvided() {
        InstitutionModel model = this.createTestModel(null);
        String searchName = "Teste";
        PageImpl<InstitutionModel> page = new PageImpl<>(List.of(model));

        when(repository.findByNameContainingIgnoreCase(eq(searchName), any(Pageable.class)))
                .thenReturn(page);

        Page<InstitutionModel> result = service.findAll(searchName, 10, 0, "name,asc");

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        assertEquals(model.getId(), result.getContent().get(0).getId());
        verify(repository).findByNameContainingIgnoreCase(eq(searchName), any(Pageable.class));
        verify(repository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("Deve buscar todas as instituições cadastradas")
    void shouldFindAllInstituitions() {
        InstitutionModel model = this.createTestModel(null);
        PageImpl<InstitutionModel> page = new PageImpl<>(List.of(model));

        when(repository.findAll(any(Pageable.class))).thenReturn(page);

        Page<InstitutionModel> result = service.findAll(null, 10, 0, "name,asc");

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        verify(repository).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("Deve buscar uma instituição cadastrada no sistema pelo id")
    void shouldFindInstitutionById() {
        UUID uuid = UUID.fromString("be230414-d7ab-4448-b100-f29bb50609bb");
        InstitutionModel model = this.createTestModel(uuid);

        when(repository.findById(uuid)).thenReturn(Optional.of(model));

        InstitutionModel result = service.findById(uuid);

        assertNotNull(result);
        assertEquals(model.getId(), result.getId());
        verify(repository).findById(uuid);
    }

    @Test
    @DisplayName("Deve lançar exceção de instituição não encontrada quando id não está cadastrado")
    void shouldThrowInstitutionNotFoundExceptionWhenIdNotFound() {
        UUID uuid = UUID.fromString("be230414-d7ab-4448-b100-f29bb50609bb");
        when(repository.findById(uuid)).thenReturn(Optional.empty());

        assertThrows(InstitutionNotFoundException.class, () -> service.findById(uuid));
        verify(repository).findById(uuid);
    }

    @Test
    @DisplayName("Deve alterar os campos da instituição com novos valores")
    void shouldUpdateInstitutionWithNewValues() {
        UUID uuid = UUID.randomUUID();
        InstitutionModel model = this.createTestModel(uuid);

        InstitutionModel updatedModel = InstitutionModel.builder()
                .name("Universidade Novo Mundo")
                .acronym("UNM")
                .phone("1101234567")
                .address("Rua de baixo, 123")
                .build();

        when(repository.findById(uuid)).thenReturn(Optional.of(model));
        when(repository.save(any(InstitutionModel.class))).thenAnswer(inv -> inv.getArgument(0));

        InstitutionModel result = service.update(uuid, updatedModel);

        assertNotNull(result);
        assertEquals(updatedModel.getName(), result.getName());
        assertEquals(updatedModel.getAcronym(), result.getAcronym());
        assertEquals(updatedModel.getPhone(), result.getPhone());
        assertEquals(updatedModel.getAddress(), result.getAddress());

        verify(repository).save(model);
    }

    @Test
    @DisplayName("Deve alterar o estado da instituição para desativo")
    void shouldToggleInstitutionStatusToDisable() {
        UUID uuid = UUID.randomUUID();
        InstitutionModel model = spy(createTestModel(uuid));
        model.setActive(true);

        when(repository.findById(uuid)).thenReturn(Optional.of(model));
        when(repository.save(model)).thenAnswer(inv -> inv.getArgument(0));

        service.toggleStatus(uuid);

        verify(model).setActive(eq(false));
    }

    @Test
    @DisplayName("Deve alterar o estado da instituição para ativo")
    void shouldToggleInstitutionStatusToActive() {
        UUID uuid = UUID.randomUUID();
        InstitutionModel model = spy(createTestModel(uuid));
        model.setActive(false);

        when(repository.findById(uuid)).thenReturn(Optional.of(model));
        when(repository.save(model)).thenAnswer(inv -> inv.getArgument(0));

        service.toggleStatus(uuid);

        verify(model).setActive(eq(true));
    }

    @Test
    @DisplayName("Deve marcar instituição como deletada")
    void shouldToggleInstitutionDeleteStatusToTrue() {
        UUID uuid = UUID.randomUUID();
        InstitutionModel model = spy(createTestModel(uuid));
        model.setDeleted(false);

        when(repository.findById(uuid)).thenReturn(Optional.of(model));
        when(repository.save(model)).thenAnswer(inv -> inv.getArgument(0));

        service.delete(uuid);

        verify(model).setDeleted(eq(true));
    }


    @Test
    @DisplayName("delete em ID inexistente → InstitutionNotFoundException sem persistir")
    void deleteOfMissingIdThrowsAndDoesNotPersist() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(InstitutionNotFoundException.class, () -> service.delete(id));

        verify(repository, never()).save(any());
    }
}
