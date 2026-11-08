package com.n0hana.echoes_server.animal;

import java.security.interfaces.ECPublicKey;
import java.util.Date;

import org.springframework.stereotype.Service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import lombok.RequiredArgsConstructor;

/**
 * Valida os tokens autoassinados pelos dispositivos.
 *
 * <p>
 * O dispositivo gera um JWT ES256 com as claims {@code iss=echoes-device},
 * {@code mac} e {@code exp}. O servidor localiza o animal pelo MAC, recupera a
 * chave pública cadastrada e valida a assinatura. Como esses tokens têm curta
 * duração, não há necessidade de revogação/lista negra.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AnimalJwtService {

    public static final String DEVICE_ISSUER = "echoes-device";
    public static final String MAC_CLAIM = "mac";

    private final AnimalRepository animalRepository;
    private final AnimalPublicKeyService publicKeyService;

    public AnimalModel validateToken(String token) throws Exception {
        SignedJWT signedJWT = SignedJWT.parse(token);
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

        if (!DEVICE_ISSUER.equals(claims.getIssuer()))
            throw new SecurityException("Invalid issuer");

        if (!JWSAlgorithm.ES256.equals(signedJWT.getHeader().getAlgorithm()))
            throw new SecurityException("Invalid algorithm");

        String mac = claims.getStringClaim(MAC_CLAIM);
        if (mac == null || mac.isBlank())
            throw new SecurityException("Missing mac claim");

        AnimalModel animal = animalRepository
                .findByMac(mac.trim())
                .orElseThrow(() -> new SecurityException("Device not found"));

        ECPublicKey publicKey = publicKeyService.parse(animal.getPublicKey());

        if (!signedJWT.verify(new ECDSAVerifier(publicKey)))
            throw new SecurityException("Invalid JWT signature");

        Date expiration = claims.getExpirationTime();
        if (expiration == null || expiration.before(new Date()))
            throw new SecurityException("JWT expired");

        return animal;
    }
}
