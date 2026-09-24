package com.n0hana.echoes_server.term;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.term.model.UserTermAcceptance;

@Repository
public interface UserTermAcceptanceRepository extends JpaRepository<UserTermAcceptance, UUID> {
    @Query("SELECT uta FROM UserTermAcceptance uta WHERE uta.user.id = :userId AND uta.term.id = :termsId")
    Optional<UserTermAcceptance> findByUserIdAndTermsId(@Param("userId") UUID userId, @Param("termsId") Long termsId);

    @Query("SELECT uta FROM UserTermAcceptance uta WHERE uta.user.id = :userId AND uta.term.type = :type ORDER BY uta.acceptedAt DESC")
    Optional<UserTermAcceptance> findLatestByUserIdAndType(@Param("userId") UUID userId, @Param("type") DocumentType type);

    @Modifying
    @Query("DELETE FROM UserTermAcceptance uta WHERE uta.user.id = :userId")
    void deleteAll(@Param("userId") UUID userId);
}
