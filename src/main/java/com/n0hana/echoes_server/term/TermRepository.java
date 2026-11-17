package com.n0hana.echoes_server.term;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.term.model.TermModel;
import com.n0hana.echoes_server.term.model.TermStatus;

public interface TermRepository extends JpaRepository<TermModel, Long> {

    Optional<TermModel> findFirstByTypeAndStatusOrderByTimestampDesc(
        DocumentType type,
        TermStatus status
    );
    Optional<TermModel> findTopByTypeOrderByTimestampDesc(DocumentType type);

    /**
     * Tipos que possuem termo PUBLISHED mas cujo usuário ainda não registrou aceite
     * para aquele termo específico.
     */
    @Query("""
        SELECT DISTINCT t.type
        FROM TermModel t
        WHERE t.status = :status
          AND t.type IN :types
          AND NOT EXISTS (
              SELECT uta.id
              FROM UserTermAcceptance uta
              WHERE uta.user.id = :userId
                AND uta.term.id = t.id
          )
        """)
    List<DocumentType> findTypesWithoutAcceptance(
        @Param("userId") UUID userId,
        @Param("types") Collection<DocumentType> types,
        @Param("status") TermStatus status
    );
}
