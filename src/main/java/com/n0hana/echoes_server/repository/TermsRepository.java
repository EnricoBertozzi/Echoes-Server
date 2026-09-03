package com.n0hana.echoes_server.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.n0hana.echoes_server.model.DocumentType;
import com.n0hana.echoes_server.model.Terms;

@Repository
public interface TermsRepository extends JpaRepository<Terms, Long> {
    Optional<Terms> findByTypeAndActiveTrue(DocumentType type);
    Optional<Terms> findTopByTypeOrderByTimestampDesc(DocumentType type);
}