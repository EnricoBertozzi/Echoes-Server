package com.n0hana.echoes_server.device;

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

import com.n0hana.echoes_server.animal.AnimalModel;
import com.n0hana.echoes_server.infra.security.JwtBlackListRepository;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

@ExtendWith(MockitoExtension.class)
public class DeviceJwtServiceTests {

    private static final String MAC = "AA:BB:CC:DD:EE:FF";

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private JwtBlackListRepository blackList;

    @Mock
    private DeviceTokenRevocationRepository tokenRevocations;

    private final DevicePublicKeyService publicKeyService = new DevicePublicKeyService();

    private DeviceJwtService deviceJwtService;
    private KeyPair keyPair;
    private DeviceModel device;

    @BeforeEach
    void setUp() throws Exception {
        deviceJwtService = new DeviceJwtService(deviceRepository, publicKeyService, blackList, tokenRevocations);

        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = generator.generateKeyPair();

        device = DeviceModel.builder()
                .id(UUID.randomUUID())
                .name("Dispositivo de teste")
                .hardwareId(MAC)
                .publicKey(toPem(keyPair))
                .animal(AnimalModel.builder().id(UUID.randomUUID()).name("Cachorro").build())
                .build();
    }

    @Test
    @DisplayName("Aceita token ES256 com iss echoes-device e hardwareId cadastrado")
    void shouldValidateDeviceToken() throws Exception {
        when(deviceRepository.findByHardwareId(MAC)).thenReturn(Optional.of(device));

        String token = createToken(MAC, Instant.now().plus(1, ChronoUnit.MINUTES), keyPair);

        DeviceModel result = deviceJwtService.validateToken(token);

        assertEquals(device, result);
    }

    @Test
    @DisplayName("Rejeita a claim antiga mac: só hardware_id é aceito (S12)")
    void shouldRejectLegacyMacClaim() throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.ES256),
                new JWTClaimsSet.Builder()
                        .issuer(DeviceJwtService.DEVICE_ISSUER)
                        .claim("mac", MAC)
                        .issueTime(new Date())
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.MINUTES)))
                        .build());
        jwt.sign(new ECDSASigner((ECPrivateKey) keyPair.getPrivate()));

        assertThrows(Exception.class, () -> deviceJwtService.validateToken(jwt.serialize()));
    }

    @Test
    @DisplayName("Rejeita token sem iat (S12)")
    void shouldRejectTokenWithoutIat() throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.ES256),
                new JWTClaimsSet.Builder()
                        .issuer(DeviceJwtService.DEVICE_ISSUER)
                        .claim(DeviceJwtService.HARDWARE_ID_CLAIM, MAC)
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.MINUTES)))
                        .build());
        jwt.sign(new ECDSASigner((ECPrivateKey) keyPair.getPrivate()));

        assertThrows(Exception.class, () -> deviceJwtService.validateToken(jwt.serialize()));
    }

    @Test
    @DisplayName("Rejeita token com vida maior que 5 minutos (S12)")
    void shouldRejectTokenWithLifetimeOverFiveMinutes() throws Exception {
        when(deviceRepository.findByHardwareId(MAC)).thenReturn(Optional.of(device));
        String token = createToken(MAC, Instant.now().plus(30, ChronoUnit.MINUTES), keyPair);

        assertThrows(Exception.class, () -> deviceJwtService.validateToken(token));
    }

    @Test
    @DisplayName("Aceita dispositivo sem animal associado")
    void shouldValidateDeviceWithoutAnimal() throws Exception {
        device.setAnimal(null);
        when(deviceRepository.findByHardwareId(MAC)).thenReturn(Optional.of(device));

        String token = createToken(MAC, Instant.now().plus(1, ChronoUnit.MINUTES), keyPair);

        DeviceModel result = deviceJwtService.validateToken(token);

        assertEquals(device, result);
    }

    @Test
    @DisplayName("Rejeita token de MAC não cadastrado")
    void shouldRejectUnknownMac() throws Exception {
        when(deviceRepository.findByHardwareId(MAC)).thenReturn(Optional.empty());

        String token = createToken(MAC, Instant.now().plus(1, ChronoUnit.MINUTES), keyPair);

        assertThrows(SecurityException.class, () -> deviceJwtService.validateToken(token));
    }

    @Test
    @DisplayName("Rejeita token expirado")
    void shouldRejectExpiredToken() throws Exception {
        when(deviceRepository.findByHardwareId(MAC)).thenReturn(Optional.of(device));

        String token = createToken(MAC, Instant.now().minus(1, ChronoUnit.MINUTES), keyPair);

        assertThrows(SecurityException.class, () -> deviceJwtService.validateToken(token));
    }

    @Test
    @DisplayName("Rejeita token assinado com chave diferente da cadastrada")
    void shouldRejectTokenSignedWithAnotherKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair anotherKeyPair = generator.generateKeyPair();

        when(deviceRepository.findByHardwareId(MAC)).thenReturn(Optional.of(device));

        String token = createToken(MAC, Instant.now().plus(1, ChronoUnit.MINUTES), anotherKeyPair);

        assertThrows(SecurityException.class, () -> deviceJwtService.validateToken(token));
    }

    @Test
    @DisplayName("Rejeita token com issuer diferente de echoes-device")
    void shouldRejectInvalidIssuer() throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.ES256),
                new JWTClaimsSet.Builder()
                        .issuer("echoes-server")
                        .claim(DeviceJwtService.HARDWARE_ID_CLAIM, MAC)
                        .issueTime(new Date()).expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.MINUTES)))
                        .build());
        jwt.sign(new ECDSASigner((ECPrivateKey) keyPair.getPrivate()));

        assertThrows(SecurityException.class, () -> deviceJwtService.validateToken(jwt.serialize()));
    }

    @Test
    @DisplayName("Rejeita token sem a claim hardware_id")
    void shouldRejectTokenWithoutMac() throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.ES256),
                new JWTClaimsSet.Builder()
                        .issuer(DeviceJwtService.DEVICE_ISSUER)
                        .issueTime(new Date()).expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.MINUTES)))
                        .build());
        jwt.sign(new ECDSASigner((ECPrivateKey) keyPair.getPrivate()));

        assertThrows(SecurityException.class, () -> deviceJwtService.validateToken(jwt.serialize()));
    }

    private String createToken(String hardwareId, Instant expiration, KeyPair signerKeyPair) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.ES256),
                new JWTClaimsSet.Builder()
                        .issuer(DeviceJwtService.DEVICE_ISSUER)
                        .claim(DeviceJwtService.HARDWARE_ID_CLAIM, hardwareId)
                        .jwtID(UUID.randomUUID().toString())
                        .issueTime(new Date()).expirationTime(Date.from(expiration))
                        .build());

        jwt.sign(new ECDSASigner((ECPrivateKey) signerKeyPair.getPrivate()));
        return jwt.serialize();
    }

    private String tokenWith(String jti, Instant issuedAt, Instant expiration) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(DeviceJwtService.DEVICE_ISSUER)
                .claim(DeviceJwtService.HARDWARE_ID_CLAIM, MAC)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiration));
        if (jti != null)
            claims.jwtID(jti);
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.ES256), claims.build());
        jwt.sign(new ECDSASigner((ECPrivateKey) keyPair.getPrivate()));
        return jwt.serialize();
    }

    @Test
    @DisplayName("Rejeita token sem jti")
    void shouldRejectTokenWithoutJti() throws Exception {
        when(deviceRepository.findByHardwareId(MAC)).thenReturn(Optional.of(device));
        String token = tokenWith(null, Instant.now(), Instant.now().plus(1, ChronoUnit.MINUTES));

        SecurityException error = assertThrows(SecurityException.class, () -> deviceJwtService.validateToken(token));
        assertEquals("Missing jti claim", error.getMessage());
        assertThrows(com.n0hana.echoes_server.device.exception.DeviceAuthenticationException.class,
                () -> deviceJwtService.verify(token));
    }

    @Test
    @DisplayName("Rejeita token com jti em branco")
    void shouldRejectTokenWithBlankJti() throws Exception {
        when(deviceRepository.findByHardwareId(MAC)).thenReturn(Optional.of(device));
        String token = tokenWith("  ", Instant.now(), Instant.now().plus(1, ChronoUnit.MINUTES));

        assertThrows(SecurityException.class, () -> deviceJwtService.validateToken(token));
    }

    @Test
    @DisplayName("Rejeita token cujo jti está na blacklist")
    void shouldRejectBlacklistedJti() throws Exception {
        when(deviceRepository.findByHardwareId(MAC)).thenReturn(Optional.of(device));
        when(blackList.exists("jti-revogado")).thenReturn(true);
        String token = tokenWith("jti-revogado", Instant.now(), Instant.now().plus(1, ChronoUnit.MINUTES));

        assertThrows(SecurityException.class, () -> deviceJwtService.validateToken(token));
    }

    @Test
    @DisplayName("revokeToken coloca o jti na blacklist com TTL até o exp; token já expirado é ignorado")
    void revokeTokenPopulatesBlacklist() {
        Instant exp = Instant.now().plus(4, ChronoUnit.MINUTES);

        deviceJwtService.revokeToken("jti-1", exp);

        org.mockito.ArgumentCaptor<java.time.Duration> ttl = org.mockito.ArgumentCaptor.forClass(java.time.Duration.class);
        org.mockito.Mockito.verify(blackList).save(org.mockito.ArgumentMatchers.eq("jti-1"), ttl.capture());
        org.junit.jupiter.api.Assertions.assertTrue(ttl.getValue().toSeconds() > 230 && ttl.getValue().toSeconds() <= 240);

        deviceJwtService.revokeToken("jti-2", Instant.now().minusSeconds(5));
        org.mockito.Mockito.verify(blackList, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.eq("jti-2"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("revokeAllForDevice grava o corte; token emitido antes é rejeitado e o emitido depois é aceito")
    void revokeAllForDeviceCutsOffOlderTokens() throws Exception {
        when(deviceRepository.findByHardwareId(MAC)).thenReturn(Optional.of(device));
        long cutoff = Instant.now().getEpochSecond();

        deviceJwtService.revokeAllForDevice(device.getId());
        org.mockito.Mockito.verify(tokenRevocations)
                .revokeUntil(org.mockito.ArgumentMatchers.eq(device.getId()), org.mockito.ArgumentMatchers.longThat(v -> v >= cutoff));

        when(tokenRevocations.findCutoff(device.getId())).thenReturn(Optional.of(cutoff));
        String before = tokenWith("a", Instant.ofEpochSecond(cutoff - 10), Instant.now().plus(1, ChronoUnit.MINUTES));
        String after = tokenWith("b", Instant.ofEpochSecond(cutoff + 1), Instant.now().plus(1, ChronoUnit.MINUTES));

        assertThrows(SecurityException.class, () -> deviceJwtService.validateToken(before));
        assertEquals(device, deviceJwtService.validateToken(after));
    }

    private String toPem(KeyPair pair) {
        String base64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
    }
}
