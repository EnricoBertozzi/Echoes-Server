package com.n0hana.echoes_server.animal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

@ExtendWith(MockitoExtension.class)
public class AnimalJwtServiceTests {

    private static final String MAC = "AA:BB:CC:DD:EE:FF";

    @Mock
    private AnimalRepository animalRepository;

    private final AnimalPublicKeyService publicKeyService = new AnimalPublicKeyService();

    private AnimalJwtService animalJwtService;
    private KeyPair keyPair;
    private AnimalModel animal;

    @BeforeEach
    void setUp() throws Exception {
        animalJwtService = new AnimalJwtService(animalRepository, publicKeyService);

        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = generator.generateKeyPair();

        animal = AnimalModel.builder()
                .id(UUID.randomUUID())
                .name("Dispositivo de teste")
                .mac(MAC)
                .publicKey(toPem(keyPair))
                .build();
    }

    @Test
    @DisplayName("Aceita token ES256 com iss echoes-device e mac cadastrado")
    void shouldValidateDeviceToken() throws Exception {
        when(animalRepository.findByMac(MAC)).thenReturn(Optional.of(animal));

        String token = createToken(MAC, Instant.now().plus(1, ChronoUnit.MINUTES), keyPair);

        AnimalModel result = animalJwtService.validateToken(token);

        assertEquals(animal, result);
    }

    @Test
    @DisplayName("Rejeita token de MAC não cadastrado")
    void shouldRejectUnknownMac() throws Exception {
        when(animalRepository.findByMac(MAC)).thenReturn(Optional.empty());

        String token = createToken(MAC, Instant.now().plus(1, ChronoUnit.MINUTES), keyPair);

        assertThrows(SecurityException.class, () -> animalJwtService.validateToken(token));
    }

    @Test
    @DisplayName("Rejeita token expirado")
    void shouldRejectExpiredToken() throws Exception {
        when(animalRepository.findByMac(MAC)).thenReturn(Optional.of(animal));

        String token = createToken(MAC, Instant.now().minus(1, ChronoUnit.MINUTES), keyPair);

        assertThrows(SecurityException.class, () -> animalJwtService.validateToken(token));
    }

    @Test
    @DisplayName("Rejeita token assinado com chave diferente da cadastrada")
    void shouldRejectTokenSignedWithAnotherKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair anotherKeyPair = generator.generateKeyPair();

        when(animalRepository.findByMac(MAC)).thenReturn(Optional.of(animal));

        String token = createToken(MAC, Instant.now().plus(1, ChronoUnit.MINUTES), anotherKeyPair);

        assertThrows(SecurityException.class, () -> animalJwtService.validateToken(token));
    }

    @Test
    @DisplayName("Rejeita token com issuer diferente de echoes-device")
    void shouldRejectInvalidIssuer() throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.ES256),
                new JWTClaimsSet.Builder()
                        .issuer("echoes-server")
                        .claim(AnimalJwtService.MAC_CLAIM, MAC)
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.MINUTES)))
                        .build());
        jwt.sign(new ECDSASigner((ECPrivateKey) keyPair.getPrivate()));

        assertThrows(SecurityException.class, () -> animalJwtService.validateToken(jwt.serialize()));
    }

    private String createToken(String mac, Instant expiration, KeyPair signerKeyPair) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.ES256),
                new JWTClaimsSet.Builder()
                        .issuer(AnimalJwtService.DEVICE_ISSUER)
                        .claim(AnimalJwtService.MAC_CLAIM, mac)
                        .expirationTime(Date.from(expiration))
                        .build());

        jwt.sign(new ECDSASigner((ECPrivateKey) signerKeyPair.getPrivate()));
        return jwt.serialize();
    }

    private String toPem(KeyPair pair) {
        String base64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
    }
}
