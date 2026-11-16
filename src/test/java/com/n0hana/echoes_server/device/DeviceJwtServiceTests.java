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

    private final DevicePublicKeyService publicKeyService = new DevicePublicKeyService();

    private DeviceJwtService deviceJwtService;
    private KeyPair keyPair;
    private DeviceModel device;

    @BeforeEach
    void setUp() throws Exception {
        deviceJwtService = new DeviceJwtService(deviceRepository, publicKeyService);

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
    @DisplayName("Aceita a claim antiga mac enquanto o firmware migra")
    void shouldAcceptLegacyMacClaim() throws Exception {
        when(deviceRepository.findByHardwareId(MAC)).thenReturn(Optional.of(device));

        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.ES256),
                new JWTClaimsSet.Builder()
                        .issuer(DeviceJwtService.DEVICE_ISSUER)
                        .claim(DeviceJwtService.LEGACY_CLAIM, MAC)
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.MINUTES)))
                        .build());
        jwt.sign(new ECDSASigner((ECPrivateKey) keyPair.getPrivate()));

        assertEquals(device, deviceJwtService.validateToken(jwt.serialize()));
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
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.MINUTES)))
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
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.MINUTES)))
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
