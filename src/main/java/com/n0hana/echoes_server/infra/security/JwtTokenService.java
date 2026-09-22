package com.n0hana.echoes_server.infra.security;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.n0hana.echoes_server.auth.exception.AuthFailedException;
import com.n0hana.echoes_server.user.model.User;

/**
 * Service para manipulação e verificação de tokens JWT
 * 
 * @author Enrico Bertozzi
 * @since 0.1.1
 */
@Service
public class JwtTokenService {

    @Value("${api.security.token.secret}")
    private String secret;

    private String ISSUER = "echoes-server";

    @Autowired 
    private JwtBlackListRepository blackListRepository;

    /**
     * Gera o token JWT para autenticação de usuários no sistema
     * 
     * @param user Usuário que está realizando a autenticação
     * 
     * @return {@link String} contendo o token JWT gerado pela aplicação
     */
    public String generate(User user) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            UUID jti = UUID.randomUUID();
            String token = JWT.create()
                    .withIssuer(ISSUER)
                    .withSubject(user.getId().toString())
                    .withJWTId(jti.toString())
                    .withExpiresAt(genExpirationDate())
                    .sign(algorithm);
            return token;

        } catch (JWTCreationException ex) {
            throw new AuthFailedException();
        }
    }

    /**
     * Verifica se o token JWT foi criado e assinato pelo sistema
     * 
     * @param token Token JWT a ser analisado
     * 
     * @return {@link String} contendo o UUID do usuário do subject do token
     */
    public String validate(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .build()
                    .verify(token)
                    .getSubject();

        } catch (JWTVerificationException e) {
            throw new AuthFailedException();
        }
    }

    /**
     * Gera a hora de expiração dos tokens JWT de forma dinâmica
     * 
     * @return {@link Instant} com o horário de expiração do token
     */
    private Instant genExpirationDate() {
        return LocalDateTime.now().plusHours(2).toInstant(ZoneOffset.of("-03:00"));
    }

    /**
     * Extrai o token JWT do cabeçalho de autorização
     * 
     * @return {@link String} sendo o token de JWT
     */
    public String recoverToken(String header) {
        if (header == null)
            return null;
        return header.replace("Bearer ", "");
    }

    /**
     * Invalida o token JWT de um usuário adicionando em uma blacklist
     * 
     * @param header Cabeçalho com o token JWT.
     */
    public void invalidate(String header) {
        String token = this.recoverToken(header);
        if (token == null)
            throw new AuthFailedException();

        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            DecodedJWT decodedJWT = JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .build()
                    .verify(token);

            String jti = decodedJWT.getId();
            Instant expiresAt = decodedJWT.getExpiresAtAsInstant();
            Instant now = Instant.now();

            if (expiresAt.isAfter(now)) {
                Duration duration = Duration.between(now, expiresAt);
                blackListRepository.save(jti, duration);
            }
        } catch (JWTVerificationException ex) {
            throw new AuthFailedException();
        }
    }

    /**
     * Verifica se um token foi revogado e está presente na blacklist
     * 
     * @param token Token a ser verificado
     * @return {@link Boolean} informando se está revogado ou não
     */
    public boolean isRevoked(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            String jti = JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .build()
                    .verify(token)
                    .getId();

            return blackListRepository.exists(jti);
        } catch (JWTVerificationException e) {
            return true;
        }
    }

}
