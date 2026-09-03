package com.n0hana.echoes_server.service.auth;

import com.n0hana.echoes_server.repository.TokenRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.n0hana.echoes_server.model.Token;
import com.n0hana.echoes_server.model.User;
import com.n0hana.echoes_server.repository.JwtTokenRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final TokenRepository tokenRepository;
    private final JwtTokenRepository jwtTokenRepository;
    private final String ISSUER = "auth-api";

    @Value("${api.security.token.secret}")
    private String secret;


    /**==================================
     *  CRIAÇÃO DO TOKEN JWT
     * ==================================
     * Após a autenticação primária o 
     * método é chamado para fornecidmento
     * do código único de validação
    */
    public String generateToken(User user) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            UUID jti = UUID.randomUUID();
            String jtiStr = jti.toString();
            String token = JWT.create()
                .withIssuer(ISSUER)
                .withSubject(user.getId().toString())
                .withJWTId(jtiStr)
                .withExpiresAt(genExpirationDate())
                .sign(algorithm);

                tokenRepository.save(
                    new Token(null, jtiStr, false, user)
                );

            return token;
        } catch (JWTCreationException exception) {
            throw new RuntimeException("Erro ao criar token JWT");
        }
    }

    /**==================================
     *  VALIDAÇÃO DO TOKEN JWT
     * ==================================
     * Realiza a validação do token JWT e
     * retorna seu subject.
    */
    public String validadeToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                .withIssuer(ISSUER)
                .build()
                .verify(token)
                .getSubject();    
                
        } catch (JWTVerificationException e) {
            System.err.println("Erro na verificação: " + e.getMessage());
            return "";
        }
    }

    private Instant genExpirationDate() {
        return LocalDateTime.now().plusHours(2).toInstant(ZoneOffset.of("-03:00"));
    }

    /**===========================
     * TOKEN DE ALTERAÇÃO DE SENHA
     * =========================== */

    /** CRIAÇÃO DO TOKEN DE ALTERAÇÃO */
    public String generatePasswordChangeToken(User user) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            UUID jti = UUID.randomUUID();
            String jtiStr = jti.toString();
            String token = JWT.create()
                .withIssuer(ISSUER)
                .withSubject(user.getId().toString())
                .withJWTId(jtiStr)
                .withExpiresAt(genExpirationDatePasswordToken())
                .sign(algorithm);

            return token;

        } catch (JWTCreationException e) {
            throw new RuntimeException("Erro ao criar token JWT");
        }
    }

    /** VALIDAÇÃO DO TOKEN DE ALTERAÇÃO */
    public String validatePasswordChangeToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                .withIssuer(ISSUER)
                .build()
                .verify(token)
                .getSubject();    
                
        } catch (JWTVerificationException e) {
            System.err.println("Erro na verificação: " + e.getMessage());
            return "";
        }
    }

    public Instant genExpirationDatePasswordToken() {
        return LocalDateTime.now().plusMinutes(5).toInstant(ZoneOffset.of("-03:00")); 
    }


    public String extractJti(String token) {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        return JWT.require(algorithm)
            .withIssuer(ISSUER)
            .build()
            .verify(token)
            .getId(); // <-- isso é o JTI
    }

    public void save(String jti, User user) {
        this.revokeAll(user);

        Token token = new Token(
            null,
            jti,
            false,
            user
        );

        jwtTokenRepository.save(token);;
    }

    public void revokeAll(User user) {
        List<Token> tokens = jwtTokenRepository.findAllByUserId(user.getId()); 

        tokens.forEach(t -> t.setRevoked(true));

        jwtTokenRepository.saveAll(tokens);
    }

    public Optional<Token> findByJti(String jti) {
        return jwtTokenRepository.findByJti(jti);
    }



}
