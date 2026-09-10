package com.n0hana.echoes_server.institution;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InstitutionRepository extends JpaRepository<InstitutionModel, UUID> {
    boolean existsByCnpjOrEmail(String cnpj, String email);
    Optional<InstitutionModel> findByCnpj(String cnpj);
    Page<InstitutionModel> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
