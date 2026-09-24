package com.n0hana.echoes_server.term;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.term.model.TermModel;
import com.n0hana.echoes_server.term.model.TermStatus;

@Repository
public interface TermRepository extends JpaRepository<TermModel, Long> {

    Optional<TermModel> findFirstByTypeAndStatusOrderByTimestampDesc(
        DocumentType type,
        TermStatus status
    );
    Optional<TermModel> findTopByTypeOrderByTimestampDesc(DocumentType type);
}
