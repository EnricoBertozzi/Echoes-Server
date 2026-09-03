package com.n0hana.echoes_server.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.n0hana.echoes_server.model.Token;


public interface JwtTokenRepository extends JpaRepository<Token, Long> {
    
    Optional<Token> findByJti(String jti);

    List<Token> findAllByUserId(UUID userId);
}
