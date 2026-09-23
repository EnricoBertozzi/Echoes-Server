package com.n0hana.echoes_server.institution;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    boolean existsByCnpjOrEmail(String cnpj, String email);

    Optional<InstitutionModel> findByCnpj(String cnpj);

    Page<InstitutionModel> findByNameContainingIgnoreCase(String name, Pageable pageable);

    /**
     * Lista instituições por status de verificação. Usado pelo scheduler para
     * buscar todas as {@code PENDING_VERIFICATION} e retentar.
     */
    List<InstitutionModel> findAllByVerificationStatus(InstitutionVerificationStatus status);
}
