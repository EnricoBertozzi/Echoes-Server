package com.n0hana.echoes_server.repository;

import com.n0hana.echoes_server.model.Token;
import com.n0hana.echoes_server.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TokenRepository extends JpaRepository<Token, Long> {

    @Query("""
            SELECT t FROM Token t INNER JOIN User u ON t.user.id = user.id
            WHERE u.id = :userId AND t.revoked
            """)
    List<Token> findAllByUserId(Long userId);

    Optional<Token> findByJti(String jti);

    @Modifying
    void deleteByUser(User user);

}
