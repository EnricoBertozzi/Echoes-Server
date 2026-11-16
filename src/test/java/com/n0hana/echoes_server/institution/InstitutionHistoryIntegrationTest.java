package com.n0hana.echoes_server.institution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.n0hana.echoes_server.user.model.Admin;

@SpringBootTest
@ActiveProfiles("test")
class InstitutionHistoryIntegrationTest {

    @Autowired
    private InstitutionRepository repository;

    @Autowired
    private InstitutionHistoryService historyService;

    private Admin admin;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Mudança de status registra quem, quando e como estava antes")
    void shouldRegisterStatusChange() {
        InstitutionModel institution = repository.save(newInstitution("Instituição Teste"));
        loginAsAdmin();

        institution.setActive(false);
        repository.save(institution);

        List<InstitutionChangeDTO> history = historyService.findHistory(institution.getId(), 50);

        assertEquals(1, history.size());
        InstitutionChangeDTO change = history.get(0);
        assertEquals("active", change.property());
        assertEquals(true, change.before());
        assertEquals(false, change.after());
        assertEquals(admin.getId().toString(), change.author());
        assertNotNull(change.date());
    }

    @Test
    @DisplayName("Mudança de dados registra o valor anterior e ignora campos de ruído")
    void shouldRegisterDataChangeWithoutNoise() {
        InstitutionModel institution = repository.save(newInstitution("Nome Antigo"));
        loginAsAdmin();

        institution.setName("Nome Novo");
        repository.save(institution);

        List<InstitutionChangeDTO> history = historyService.findHistory(institution.getId(), 50);

        // só o nome mudou, updatedAt não aparece
        assertEquals(1, history.size());
        assertEquals("name", history.get(0).property());
        assertEquals("Nome Antigo", history.get(0).before());
        assertEquals("Nome Novo", history.get(0).after());
    }

    private void loginAsAdmin() {
        admin = new Admin();
        admin.setId(UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));
    }

    private InstitutionModel newInstitution(String name) {
        String unique = UUID.randomUUID().toString().replace("-", "");

        return InstitutionModel.builder()
                .name(name)
                .acronym("INS")
                .cnpj(unique.substring(0, 14))
                .email(unique + "@teste.com")
                .build();
    }
}
