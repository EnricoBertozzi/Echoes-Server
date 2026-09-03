package com.n0hana.echoes_server.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.n0hana.echoes_server.model.DocumentType;
import com.n0hana.echoes_server.model.UserTermsAcceptance;

@Repository
public interface UserTermsAcceptanceRepository extends JpaRepository<UserTermsAcceptance, UUID> {
    @Query("SELECT uta FROM UserTermsAcceptance uta WHERE uta.user.id = :userId AND uta.terms.id = :termsId")
    Optional<UserTermsAcceptance> findByUserIdAndTermsId(@Param("userId") UUID userId, @Param("termsId") Long termsId);

    @Query("SELECT uta FROM UserTermsAcceptance uta WHERE uta.user.id = :userId AND uta.terms.type = :type ORDER BY uta.acceptedAt DESC")
    Optional<UserTermsAcceptance> findLatestByUserIdAndType(@Param("userId") UUID userId, @Param("type") DocumentType type);

    @Modifying
    @Query("DELETE FROM UserTermsAcceptance WHERE user.id = :userId")
    void deleteAll(@Param("userId") UUID userId);
}
