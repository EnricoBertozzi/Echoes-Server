package com.n0hana.echoes_server.scenario;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório JPA para armazenamento de cenários clínicos
 * 
 * @author Enrico Bertozzi
 * @since 0.1.0
 * @see {@link ScenarioModel}
 */
public interface ScenarioRepository extends JpaRepository<ScenarioModel, UUID> {

  /**
   * Busca por todos cenários de um ponto de ausculta.
   * 
   * @param auscultationId Identificador do ponto de ausculta.
   * @param pageable       Configuração de paginação
   * 
   * @return {@link Page} com todos cenários de um ponto de ausculta
   */
  Page<ScenarioModel> findAllScenariosByAuscultationPointId(UUID auscultationId, Pageable pageable);

  /**
   * Busca por todos os cenários por nome
   * 
   * @param name     Nome a ser filtrado
   * @param pageable Configuração de paginação
   * 
   * @return {@link Page} com cenários que atendam ao predicado.
   */
  Page<ScenarioModel> findAllScenariosByNameContaining(String name, Pageable pageable);
}
