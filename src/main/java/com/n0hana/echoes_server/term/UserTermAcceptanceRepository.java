package com.n0hana.echoes_server.term;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.term.model.UserTermAcceptance;

public interface UserTermAcceptanceRepository extends JpaRepository<UserTermAcceptance, UUID> {

    /** aceite ativo (não revogado) do usuário para um termo específico */
    @Query("SELECT uta FROM UserTermAcceptance uta WHERE uta.user.id = :userId AND uta.term.id = :termsId AND uta.revokedAt IS NULL")
    Optional<UserTermAcceptance> findByUserIdAndTermsId(@Param("userId") UUID userId, @Param("termsId") Long termsId);

    @Query("SELECT uta FROM UserTermAcceptance uta WHERE uta.user.id = :userId AND uta.term.type = :type ORDER BY uta.acceptedAt DESC")
    Optional<UserTermAcceptance> findLatestByUserIdAndType(@Param("userId") UUID userId, @Param("type") DocumentType type);

    /** todo o histórico de aceites do usuário (inclui revogados), para exportação e prova */
    @Query("SELECT uta FROM UserTermAcceptance uta WHERE uta.user.id = :userId ORDER BY uta.acceptedAt DESC")
    List<UserTermAcceptance> findAllByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE UserTermAcceptance uta SET uta.revokedAt = :now WHERE uta.user.id = :userId AND uta.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
