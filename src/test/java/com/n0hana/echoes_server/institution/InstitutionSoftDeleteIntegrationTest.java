package com.n0hana.echoes_server.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.n0hana.echoes_server.institution.exception.InstitutionNotFoundException;

/**
 * Prova que o soft delete funciona de verdade: {@code @SQLRestriction} só é
 * aplicado pelo Hibernate contra um banco real, então unit tests com Mockito
 * são insuficientes para validar esse comportamento.
 */
@SpringBootTest
@ActiveProfiles("test")
class InstitutionSoftDeleteIntegrationTest {

    private static final String CNPJ_VALIDO = "19131243000197";
    private static final String EMAIL = "instituicao@teste.com";

    @Autowired
    private InstitutionRepository repository;
    @Autowired
    private InstitutionService service;
    @Autowired
    private JdbcTemplate jdbc;

    /**
     * deleteAll() do JpaRepository respeita @SQLRestriction, então não limpa
     * linhas soft-deleted. Native query via JdbcTemplate bypassa a restrição.
     */
    @BeforeEach
    void limpar() {
        jdbc.update("DELETE FROM institutions");
    }

    private InstitutionModel nova(String nome, String cnpj, String email) {
        return InstitutionModel.builder()
                .name(nome)
                .acronym(nome.substring(0, 2).toUpperCase())
                .cnpj(cnpj)
                .email(email)
                .build();
    }

    @Test
    @DisplayName("delete: registro some de findById, findAll e count")
    void softDeleteEscondeDosQueries() {
        InstitutionModel salvo = repository.saveAndFlush(nova("Instituição Teste", CNPJ_VALIDO, EMAIL));
        UUID id = salvo.getId();

        service.delete(id);

        assertThat(repository.findById(id)).isEmpty();
        assertThat(repository.findAll()).isEmpty();
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("delete: CNPJ e e-mail ficam disponíveis para novo cadastro")
    void cnpjEEmailLiberadosAposDelete() {
        InstitutionModel salvo = repository.saveAndFlush(nova("Instituição Teste", CNPJ_VALIDO, EMAIL));

        service.delete(salvo.getId());

        // @SQLRestriction esconde a linha deletada, então a checagem de
        // duplicidade devolve false e um novo cadastro com o mesmo
        // CNPJ/e-mail é aceito com um id diferente.
        assertThat(repository.existsByCnpjOrEmail(CNPJ_VALIDO, EMAIL)).isFalse();

        InstitutionModel novo = repository.saveAndFlush(
                nova("Instituição Renascida", CNPJ_VALIDO, EMAIL));

        assertThat(novo.getId()).isNotEqualTo(salvo.getId());
    }

    @Test
    @DisplayName("delete: instituição deletada não aparece na busca por nome")
    void deletedNaoApareceEmBusca() {
        repository.saveAndFlush(nova("Alfa Instituição", CNPJ_VALIDO, EMAIL));
        InstitutionModel outra = repository.saveAndFlush(
                nova("Beta Instituição", "11222333000181", "outra@teste.com"));

        service.delete(outra.getId());

        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findByNameContainingIgnoreCase("Beta", Pageable.unpaged()))
                .isEmpty();
        assertThat(repository.findByNameContainingIgnoreCase("Alfa", Pageable.unpaged()))
                .hasSize(1);
    }

    @Test
    @DisplayName("delete em ID inexistente → InstitutionNotFoundException")
    void deleteDeIdInexistenteFalha() {
        assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
                .isInstanceOf(InstitutionNotFoundException.class);
    }
}
