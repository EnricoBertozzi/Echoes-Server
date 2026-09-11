package com.n0hana.echoes_server.auth.jwt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;


public interface JwtTokenRepository extends JpaRepository<JwtToken, Long> {
    
    Optional<JwtToken> findByJti(String jti);

    List<JwtToken> findAllByUserId(UUID userId);
}
