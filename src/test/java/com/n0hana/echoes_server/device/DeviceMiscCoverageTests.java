package com.n0hana.echoes_server.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.n0hana.echoes_server.infra.security.JwtBlackListRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

class DeviceMiscCoverageTests {

    @Test
    @DisplayName("jti em branco conta como ausente: recusado por padrão e sem consulta ao blacklist quando permitido")
    void blankJtiCountsAsMissing() throws Exception {
        DeviceRepository repository = mock(DeviceRepository.class);
        JwtBlackListRepository blackList = mock(JwtBlackListRepository.class);
        DeviceTokenRevocationRepository revocations = mock(DeviceTokenRevocationRepository.class);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();
        DeviceModel device = DeviceModel.builder().id(UUID.randomUUID()).hardwareId("hw")
                .publicKey(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())).build();
        when(repository.findByHardwareId("hw")).thenReturn(Optional.of(device));
        when(revocations.findCutoff(any())).thenReturn(Optional.empty());
        DeviceJwtService service = new DeviceJwtService(repository, new DevicePublicKeyService(), blackList, revocations);

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.ES256), new JWTClaimsSet.Builder()
                .issuer(DeviceJwtService.DEVICE_ISSUER)
                .claim(DeviceJwtService.HARDWARE_ID_CLAIM, "hw")
                .jwtID("   ")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(60)))
                .build());
        jwt.sign(new ECDSASigner((ECPrivateKey) pair.getPrivate()));
        String token = jwt.serialize();

        SecurityException exception = assertThrows(SecurityException.class, () -> service.validateToken(token));
        assertEquals("Missing jti claim", exception.getMessage());

        ReflectionTestUtils.setField(service, "requireJti", false);
        assertSame(device, service.validateToken(token));
        verify(blackList, never()).exists(any());
    }

    @Test
    @DisplayName("Classes agrupadoras de DTO podem ser instanciadas")
    void dtoHolders() {
        assertNotNull(new DeviceDTO());
        assertNotNull(new DeviceScenarioDTO());
        assertNotNull(new com.n0hana.echoes_server.auth.AuthDTO());
        assertNotNull(new com.n0hana.echoes_server.dashboard.DashboardDTO());
    }
}
