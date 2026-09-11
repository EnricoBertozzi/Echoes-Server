package com.n0hana.echoes_server.auth.jwt;

import com.n0hana.echoes_server.user.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TokenRepository extends JpaRepository<JwtToken, Long> {

    @Query("""
            SELECT t FROM JwtToken t INNER JOIN User u ON t.user.id = user.id
            WHERE u.id = :userId AND t.revoked
            """)
    List<JwtToken> findAllByUserId(Long userId);

    Optional<JwtToken> findByJti(String jti);

    @Modifying
    void deleteByUser(User user);

}
