package com.n0hana.echoes_server.institution;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JPA para armazenamento das instituições da aplicação.
 * 
 * @since 0.1.0
 * @author Miguel Santana da Costa
 */
@Repository
public interface InstitutionRepository extends JpaRepository<InstitutionModel, UUID> {

    /**
     * Verifica se existe uma instituição já cadastrada com determinado email ou
     * cnpj.
     * 
     * @param cnpj  CNPJ da instituição.
     * @param email Email de contato da instituição.
     * 
     * @return Valor {@link Boolean} se existe algum instituição que atenda ao
     *         predicado.
     */
    boolean existsByCnpjOrEmail(String cnpj, String email);

    /**
     * Busca uma instituição pelo seu CNPJ.
     * 
     * @param cnpj CNPJ da instituição cadastrada.
     * @return {@link Optional} contendo o resultado da busca por CNPJ.
     */
    Optional<InstitutionModel> findByCnpj(String cnpj);

    /**
     * Busca uma lista de instituições pelo nome.
     * 
     * @param name     Nome da instituição a ser filtrada.
     * @param pageable Configuração para paginação das tuplas.
     * @return {@link Page} contendo a lista de instituições encontradas.
     */
    Page<InstitutionModel> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
