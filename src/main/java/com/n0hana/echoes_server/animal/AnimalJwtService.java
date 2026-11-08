package com.n0hana.echoes_server.animal;

import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnimalJwtService {

    private final AnimalRepository animalRepository;

    public AnimalModel validateToken(String token)
            throws Exception {

        SignedJWT signedJWT = SignedJWT.parse(token);

        // Algoritmo esperado
        if (!JWSAlgorithm.ES256.equals(
                signedJWT.getHeader().getAlgorithm())) {
            throw new SecurityException("Invalid algorithm");
        }

        String animalId = signedJWT.getJWTClaimsSet()
                .getSubject();

        AnimalModel animal = animalRepository
                .findById(UUID.fromString(animalId))
                .orElseThrow(() ->
                        new SecurityException("Device not found"));

        // Recupera a chave pública cadastrada
        ECPublicKey publicKey = parsePublicKey(
                animal.getPublicKey());

        // Valida a assinatura
        boolean valid = signedJWT.verify(
                new ECDSAVerifier(publicKey));

        if (!valid) {
            throw new SecurityException(
                    "Invalid JWT signature");
        }

        // Valida expiração
        Date expiration = signedJWT.getJWTClaimsSet()
                .getExpirationTime();

        if (expiration == null ||
                expiration.before(new Date())) {
            throw new SecurityException(
                    "JWT expired");
        }

        return animal;
    }

    private ECPublicKey parsePublicKey(String pem)
        throws Exception {

    String publicKeyContent = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");

    byte[] decoded = Base64.getDecoder()
            .decode(publicKeyContent);

    KeyFactory keyFactory = KeyFactory.getInstance("EC");

    return (ECPublicKey) keyFactory.generatePublic(
            new X509EncodedKeySpec(decoded));
}
}
