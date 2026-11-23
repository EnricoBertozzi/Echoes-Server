package com.n0hana.echoes_server.institution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.n0hana.echoes_server.institution.exception.InstitutionAlreadyExistsException;
import com.n0hana.echoes_server.user.dto.CreateInstitutionUserDTO;
import com.n0hana.echoes_server.user.exception.EmailAlreadyInUseException;
import com.n0hana.echoes_server.user.service.UserRegisterService;

@ExtendWith(MockitoExtension.class)
class InstitutionRegistrationServiceTests {

    @Mock
    private InstitutionService institutionService;

    @Mock
    private UserRegisterService userRegisterService;

    @InjectMocks
    private InstitutionRegistrationService service;

    @Test
    @DisplayName("Cria a instituição e convida o gestor para ela")
    void createsInstitutionAndInvitesManager() {
        InstitutionModel model = new InstitutionModel();
        InstitutionModel saved = InstitutionModel.builder().id(UUID.randomUUID()).build();
        when(institutionService.create(model)).thenReturn(saved);

        InstitutionModel result = service.create(model, "Gestor", "Silva", "gestor@teste.com");

        assertSame(saved, result);
        ArgumentCaptor<CreateInstitutionUserDTO> captor = ArgumentCaptor.forClass(CreateInstitutionUserDTO.class);
        verify(userRegisterService).createManager(captor.capture());
        assertEquals("Gestor", captor.getValue().name());
        assertEquals("gestor@teste.com", captor.getValue().email());
        assertEquals(saved.getId(), captor.getValue().institutionId());
    }

    @Test
    @DisplayName("Não convida gestor quando a instituição não é criada")
    void doesNotInviteWhenInstitutionFails() {
        InstitutionModel model = new InstitutionModel();
        when(institutionService.create(model)).thenThrow(new InstitutionAlreadyExistsException("Já cadastrada"));

        assertThrows(InstitutionAlreadyExistsException.class,
                () -> service.create(model, "Gestor", "Silva", "gestor@teste.com"));

        verifyNoInteractions(userRegisterService);
    }

    @Test
    @DisplayName("Falha no convite do gestor propaga o erro (a transação desfaz a instituição)")
    void inviteFailurePropagates() {
        InstitutionModel model = new InstitutionModel();
        InstitutionModel saved = InstitutionModel.builder().id(UUID.randomUUID()).build();
        when(institutionService.create(model)).thenReturn(saved);
        when(userRegisterService.createManager(org.mockito.ArgumentMatchers.any(CreateInstitutionUserDTO.class)))
                .thenThrow(new EmailAlreadyInUseException());

        assertThrows(EmailAlreadyInUseException.class,
                () -> service.create(model, "Gestor", "Silva", "gestor@teste.com"));
    }
}
