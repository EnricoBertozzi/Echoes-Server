package com.n0hana.echoes_server.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.n0hana.echoes_server.device.DeviceRegistrationResult.Status;
import com.n0hana.echoes_server.device.exception.DeviceAuthenticationException;
import com.n0hana.echoes_server.device.exception.InvalidDevicePublicKeyException;
import com.n0hana.echoes_server.device.exception.InvalidDeviceRegistrationException;
import com.n0hana.echoes_server.device.exception.InvalidDeviceSignatureException;
import com.n0hana.echoes_server.device.port.RegistrationProofVerifier;
import com.n0hana.echoes_server.infra.security.JwtBlackListRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

@ExtendWith(MockitoExtension.class)
class DeviceSecurityCoverageTests {

    private static final String HW = "AA:BB:CC:DD:EE:01";

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private JwtBlackListRepository blackList;
    @Mock
    private DeviceTokenRevocationRepository revocations;
    @Mock
    private RegistrationProofVerifier proofVerifier;

    private final DevicePublicKeyService keyService = new DevicePublicKeyService();
    private final DeviceSignatureService signatureService = new DeviceSignatureService();

    private DeviceJwtService jwtService;
    private KeyPair keyPair;
    private DeviceModel device;

    @BeforeEach
    void setUp() throws Exception {
        jwtService = new DeviceJwtService(deviceRepository, keyService, blackList, revocations);
        keyPair = generate("secp256r1");
        device = DeviceModel.builder().id(UUID.randomUUID()).name("D").hardwareId(HW)
                .publicKey(pem(keyPair)).build();
    }

    private static KeyPair generate(String curve) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec(curve));
        return generator.generateKeyPair();
    }

    private static String pem(KeyPair pair) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }

    private String token(JWTClaimsSet.Builder claims, KeyPair signer) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.ES256), claims.build());
        jwt.sign(new ECDSASigner((ECPrivateKey) signer.getPrivate()));
        return jwt.serialize();
    }

    private JWTClaimsSet.Builder validClaims() {
        return new JWTClaimsSet.Builder()
                .issuer(DeviceJwtService.DEVICE_ISSUER)
                .claim(DeviceJwtService.HARDWARE_ID_CLAIM, HW)
                .jwtID("jti-1")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(60)));
    }

    private void expectRejected(String token, String message) {
        SecurityException exception = assertThrows(SecurityException.class, () -> jwtService.validateToken(token));
        assertEquals(message, exception.getMessage());
    }

    // ---------------- DeviceJwtService ----------------

    @Test
    @DisplayName("supports reconhece o emissor do dispositivo e recusa outros emissores e lixo")
    void jwtSupports() throws Exception {
        assertTrue(jwtService.supports(token(validClaims(), keyPair)));
        assertFalse(jwtService.supports(token(validClaims().issuer("outro"), keyPair)));
        assertFalse(jwtService.supports("isto-nao-e-jwt"));
    }

    @Test
    @DisplayName("verify devolve o dispositivo e embrulha falhas em DeviceAuthenticationException")
    void jwtVerify() throws Exception {
        when(deviceRepository.findByHardwareId(HW)).thenReturn(Optional.of(device));
        when(revocations.findCutoff(device.getId())).thenReturn(Optional.empty());

        assertSame(device, jwtService.verify(token(validClaims(), keyPair)));
        assertThrows(DeviceAuthenticationException.class, () -> jwtService.verify("lixo"));
    }

    @Test
    @DisplayName("Token com emissor errado é recusado")
    void jwtWrongIssuer() throws Exception {
        expectRejected(token(validClaims().issuer("x"), keyPair), "Invalid issuer");
    }

    @Test
    @DisplayName("Token com algoritmo diferente de ES256 é recusado")
    void jwtWrongAlgorithm() throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), validClaims().build());
        jwt.sign(new MACSigner(new byte[32]));

        expectRejected(jwt.serialize(), "Invalid algorithm");
    }

    @Test
    @DisplayName("Token sem hardware_id ou com hardware_id em branco é recusado")
    void jwtMissingHardwareId() throws Exception {
        expectRejected(token(validClaims().claim(DeviceJwtService.HARDWARE_ID_CLAIM, null), keyPair),
                "Missing hardware_id claim");
        expectRejected(token(validClaims().claim(DeviceJwtService.HARDWARE_ID_CLAIM, "  "), keyPair),
                "Missing hardware_id claim");
    }

    @Test
    @DisplayName("Token de dispositivo desconhecido é recusado")
    void jwtUnknownDevice() throws Exception {
        when(deviceRepository.findByHardwareId(HW)).thenReturn(Optional.empty());

        expectRejected(token(validClaims(), keyPair), "Device not found");
    }

    @Test
    @DisplayName("Token assinado por outra chave é recusado")
    void jwtInvalidSignature() throws Exception {
        when(deviceRepository.findByHardwareId(HW)).thenReturn(Optional.of(device));

        expectRejected(token(validClaims(), generate("secp256r1")), "Invalid JWT signature");
    }

    @Test
    @DisplayName("iat no futuro além da tolerância é recusado, dentro da tolerância é aceito")
    void jwtIssuedAtFuture() throws Exception {
        when(deviceRepository.findByHardwareId(HW)).thenReturn(Optional.of(device));
        when(revocations.findCutoff(any())).thenReturn(Optional.empty());

        expectRejected(token(validClaims().issueTime(Date.from(Instant.now().plusSeconds(600))), keyPair),
                "iat in the future");
        assertSame(device,
                jwtService.validateToken(token(validClaims().issueTime(Date.from(Instant.now().plusSeconds(20))), keyPair)));
    }

    @Test
    @DisplayName("Token expirado, sem exp ou com validade longa demais é recusado")
    void jwtExpiration() throws Exception {
        when(deviceRepository.findByHardwareId(HW)).thenReturn(Optional.of(device));

        expectRejected(token(validClaims().expirationTime(Date.from(Instant.now().minusSeconds(5))), keyPair),
                "JWT expired");
        expectRejected(token(validClaims().expirationTime(null), keyPair), "JWT expired");
        expectRejected(token(validClaims().expirationTime(Date.from(Instant.now().plusSeconds(3600))), keyPair),
                "JWT lifetime too long");
    }

    @Test
    @DisplayName("Sem jti: recusado por padrão e aceito quando require-jti está desligado")
    void jwtMissingJti() throws Exception {
        when(deviceRepository.findByHardwareId(HW)).thenReturn(Optional.of(device));
        String noJti = token(validClaims().jwtID(null), keyPair);

        expectRejected(noJti, "Missing jti claim");

        ReflectionTestUtils.setField(jwtService, "requireJti", false);
        when(revocations.findCutoff(device.getId())).thenReturn(Optional.empty());
        assertSame(device, jwtService.validateToken(noJti));
        verify(blackList, never()).exists(any());
    }

    @Test
    @DisplayName("Token com jti na lista de revogados é recusado")
    void jwtBlackListed() throws Exception {
        when(deviceRepository.findByHardwareId(HW)).thenReturn(Optional.of(device));
        when(blackList.exists("jti-1")).thenReturn(true);

        expectRejected(token(validClaims(), keyPair), "JWT revoked");
    }

    @Test
    @DisplayName("Corte de revogação do dispositivo recusa tokens emitidos até o corte e aceita os posteriores")
    void jwtDeviceCutoff() throws Exception {
        when(deviceRepository.findByHardwareId(HW)).thenReturn(Optional.of(device));
        Date issued = new Date();
        String token = token(validClaims().issueTime(issued), keyPair);

        when(revocations.findCutoff(device.getId())).thenReturn(Optional.of(issued.getTime() / 1000));
        expectRejected(token, "JWT revoked");

        when(revocations.findCutoff(device.getId())).thenReturn(Optional.of(issued.getTime() / 1000 - 10));
        assertSame(device, jwtService.validateToken(token));
    }

    @Test
    @DisplayName("revokeToken grava no blacklist com o tempo restante e ignora jti vazio ou token já expirado")
    void jwtRevokeToken() {
        jwtService.revokeToken("abc", Instant.now().plusSeconds(100));
        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(blackList).save(eq("abc"), ttl.capture());
        assertTrue(ttl.getValue().getSeconds() > 90 && ttl.getValue().getSeconds() <= 100);

        jwtService.revokeToken(null, Instant.now().plusSeconds(100));
        jwtService.revokeToken("  ", Instant.now().plusSeconds(100));
        jwtService.revokeToken("old", Instant.now().minusSeconds(10));
        verify(blackList, org.mockito.Mockito.times(1)).save(any(), any());
    }

    @Test
    @DisplayName("revokeAllForDevice grava o corte com o instante atual em segundos")
    void jwtRevokeAll() {
        long before = Instant.now().getEpochSecond();
        UUID id = UUID.randomUUID();

        jwtService.revokeAllForDevice(id);

        ArgumentCaptor<Long> cutoff = ArgumentCaptor.forClass(Long.class);
        verify(revocations).revokeUntil(eq(id), cutoff.capture());
        assertTrue(cutoff.getValue() >= before && cutoff.getValue() <= before + 5);
    }

    // ---------------- DevicePublicKeyService ----------------

    @Test
    @DisplayName("PEM válido, com sequências \\n literais ou sem cabeçalho, vira ECPublicKey")
    void publicKeyParsesVariants() {
        ECPublicKey expected = (ECPublicKey) keyPair.getPublic();
        String body = Base64.getEncoder().encodeToString(expected.getEncoded());

        assertEquals(expected, keyService.parse(pem(keyPair)));
        assertEquals(expected, keyService.parse(body));
        assertEquals(expected, keyService.parse("-----BEGIN PUBLIC KEY-----\\n" + body + "\\n-----END PUBLIC KEY-----"));
    }

    @Test
    @DisplayName("PEM nulo, em branco, lixo, não-EC ou de outra curva é recusado")
    void publicKeyRejectsInvalid() throws Exception {
        assertThrows(InvalidDevicePublicKeyException.class, () -> keyService.parse(null));
        assertThrows(InvalidDevicePublicKeyException.class, () -> keyService.parse("   "));
        assertThrows(InvalidDevicePublicKeyException.class, () -> keyService.parse("não é base64!"));

        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(2048);
        String rsaPem = Base64.getEncoder().encodeToString(rsa.generateKeyPair().getPublic().getEncoded());
        assertThrows(InvalidDevicePublicKeyException.class, () -> keyService.parse(rsaPem));

        KeyPair p384 = generate("secp384r1");
        assertThrows(InvalidDevicePublicKeyException.class, () -> keyService.parse(pem(p384)));
    }

    // ---------------- DeviceSignatureService ----------------

    private byte[] message() {
        return signatureService.registrationMessage(HW, pem(keyPair));
    }

    private byte[] der(byte[] message) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(message);
        return signature.sign();
    }

    private byte[] jose(byte[] message) throws Exception {
        return new ECDSASigner((ECPrivateKey) keyPair.getPrivate())
                .sign(new JWSHeader(JWSAlgorithm.ES256), message).decode();
    }

    @Test
    @DisplayName("Mensagem de registro é canônica: contexto + hardware_id + chave, com trim e nulo vazio")
    void registrationMessageCanonical() {
        String text = new String(signatureService.registrationMessage("  X ", null), StandardCharsets.UTF_8);

        assertEquals(DeviceSignatureService.REGISTRATION_CONTEXT + "\nX\n", text);
        assertEquals(DeviceSignatureService.REGISTRATION_CONTEXT + "\nX\nK",
                new String(signatureService.registrationMessage("X", " K\n"), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Aceita assinatura JOSE e DER em base64, base64url, hex e 0x-hex")
    void signatureAcceptedEncodings() throws Exception {
        ECPublicKey key = (ECPublicKey) keyPair.getPublic();
        byte[] message = message();
        byte[] jose = jose(message);
        byte[] der = der(message);

        for (byte[] raw : new byte[][] { jose, der }) {
            signatureService.verify(key, message, Base64.getEncoder().encodeToString(raw));
            signatureService.verify(key, message, Base64.getEncoder().withoutPadding().encodeToString(raw));
            signatureService.verify(key, message, Base64.getUrlEncoder().encodeToString(raw));
            signatureService.verify(key, message, HexFormat.of().formatHex(raw));
            signatureService.verify(key, message, "0x" + HexFormat.of().formatHex(raw));
            signatureService.verify(key, message, "  0X" + HexFormat.of().withUpperCase().formatHex(raw) + " ");
        }
    }

    @Test
    @DisplayName("Aceita assinaturas cujo base64 usa o alfabeto URL-safe e variados paddings")
    void signatureUrlSafeAndPaddings() throws Exception {
        ECPublicKey key = (ECPublicKey) keyPair.getPublic();
        boolean sawUrlChars = false;
        for (int i = 0; i < 60; i++) {
            byte[] message = ("m" + i).getBytes(StandardCharsets.UTF_8);
            for (byte[] raw : new byte[][] { jose(message), der(message) }) {
                String url = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
                sawUrlChars |= url.contains("-") || url.contains("_");
                signatureService.verify(key, message, url);
            }
        }
        assertTrue(sawUrlChars);
    }

    @Test
    @DisplayName("Assinatura nula, em branco, lixo, hex inválido ou com tamanho implausível é recusada")
    void signatureRejectedFormats() throws Exception {
        ECPublicKey key = (ECPublicKey) keyPair.getPublic();
        byte[] message = message();

        for (String bad : new String[] { null, "", "   ", "!!!", "0xZZ", "0x", "0xabc",
                Base64.getEncoder().encodeToString(new byte[10]),
                HexFormat.of().formatHex(new byte[70]) }) {
            assertThrows(InvalidDeviceSignatureException.class,
                    () -> signatureService.verify(key, message, bad), "assinatura: " + bad);
        }
    }

    @Test
    @DisplayName("Assinatura bem formada mas incorreta (outra mensagem, outra chave, DER corrompido) é recusada")
    void signatureWrongContent() throws Exception {
        ECPublicKey key = (ECPublicKey) keyPair.getPublic();
        byte[] message = message();
        String joseOfOther = Base64.getEncoder().encodeToString(jose("outra".getBytes(StandardCharsets.UTF_8)));
        String derOfOther = Base64.getEncoder().encodeToString(der("outra".getBytes(StandardCharsets.UTF_8)));
        byte[] brokenDer = new byte[40];
        brokenDer[0] = 0x30;

        assertThrows(InvalidDeviceSignatureException.class, () -> signatureService.verify(key, message, joseOfOther));
        assertThrows(InvalidDeviceSignatureException.class, () -> signatureService.verify(key, message, derOfOther));
        assertThrows(InvalidDeviceSignatureException.class,
                () -> signatureService.verify(key, message, Base64.getEncoder().encodeToString(brokenDer)));
        assertThrows(InvalidDeviceSignatureException.class, () -> signatureService.verify(
                (ECPublicKey) generate("secp256r1").getPublic(), message,
                Base64.getEncoder().encodeToString(jose(message))));
    }

    @Test
    @DisplayName("Chave nula é recusada e verifyRegistrationSignature valida a mensagem canônica")
    void signatureNullKeyAndRegistration() throws Exception {
        assertThrows(InvalidDeviceSignatureException.class, () -> signatureService.verify(null, message(), "x"));

        String pem = pem(keyPair);
        String sig = Base64.getEncoder().encodeToString(jose(signatureService.registrationMessage(HW, pem)));
        ECPublicKey key = (ECPublicKey) keyPair.getPublic();

        signatureService.verifyRegistrationSignature(key, HW, pem, sig);
        assertThrows(InvalidDeviceSignatureException.class,
                () -> signatureService.verifyRegistrationSignature(key, "OUTRO", pem, sig));
    }

    // ---------------- DeviceRegistrationService ----------------

    private DeviceRegistrationService registration() {
        return new DeviceRegistrationService(deviceRepository, keyService, proofVerifier);
    }

    private DeviceRegistrationPayload payload(String hw, String key, String name) {
        return new DeviceRegistrationPayload(hw, key, "sig", name);
    }

    @Test
    @DisplayName("Payload nulo, sem hardware_id ou com hardware_id grande demais é inválido")
    void registrationInvalidHardwareId() {
        DeviceRegistrationService service = registration();
        String key = pem(keyPair);

        assertThrows(InvalidDeviceRegistrationException.class, () -> service.register(null));
        assertThrows(InvalidDeviceRegistrationException.class, () -> service.register(payload(null, key, null)));
        assertThrows(InvalidDeviceRegistrationException.class, () -> service.register(payload("  ", key, null)));
        assertThrows(InvalidDeviceRegistrationException.class,
                () -> service.register(payload("x".repeat(256), key, null)));
    }

    @Test
    @DisplayName("Payload sem chave pública ou com chave grande demais é inválido")
    void registrationInvalidPublicKey() {
        DeviceRegistrationService service = registration();

        assertThrows(InvalidDeviceRegistrationException.class, () -> service.register(payload(HW, null, null)));
        assertThrows(InvalidDeviceRegistrationException.class, () -> service.register(payload(HW, " ", null)));
        assertThrows(InvalidDeviceRegistrationException.class,
                () -> service.register(payload(HW, "k".repeat(8193), null)));
    }

    @Test
    @DisplayName("Registro novo cria dispositivo PENDING com nome informado e verifica a assinatura")
    void registrationCreatesPending() {
        when(deviceRepository.findByHardwareId(HW)).thenReturn(Optional.empty());
        when(deviceRepository.saveAndFlush(any())).thenAnswer(inv -> {
            DeviceModel saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        DeviceRegistrationResult result = registration().register(payload("  " + HW + " ", pem(keyPair), "  Meu ESP "));

        assertEquals(Status.REGISTERED, result.status());
        assertEquals(DeviceStatus.PENDING, result.deviceStatus());
        assertNotNull(result.deviceId());
        ArgumentCaptor<DeviceModel> captor = ArgumentCaptor.forClass(DeviceModel.class);
        verify(deviceRepository).saveAndFlush(captor.capture());
        assertEquals("Meu ESP", captor.getValue().getName());
        assertEquals(HW, captor.getValue().getHardwareId());
        verify(proofVerifier).verifyRegistrationSignature(any(), eq(HW), eq(pem(keyPair)), eq("sig"));
    }

    @Test
    @DisplayName("Nome ausente, em branco ou maior que 255 usa o hardware_id")
    void registrationNameFallback() {
        when(deviceRepository.findByHardwareId(HW)).thenReturn(Optional.empty());
        when(deviceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        DeviceRegistrationService service = registration();

        for (String name : new String[] { null, "  ", "n".repeat(256) })
            service.register(payload(HW, pem(keyPair), name));

        ArgumentCaptor<DeviceModel> captor = ArgumentCaptor.forClass(DeviceModel.class);
        verify(deviceRepository, org.mockito.Mockito.times(3)).saveAndFlush(captor.capture());
        captor.getAllValues().forEach(saved -> assertEquals(HW, saved.getName()));
    }

    @Test
    @DisplayName("Registro repetido com a mesma chave devolve ALREADY_REGISTERED sem salvar")
    void registrationAlreadyRegistered() {
        when(deviceRepository.findByHardwareId(HW)).thenReturn(Optional.of(device));

        DeviceRegistrationResult result = registration().register(payload(HW, pem(keyPair), null));

        assertEquals(Status.ALREADY_REGISTERED, result.status());
        assertEquals(device.getId(), result.deviceId());
        verify(deviceRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Registro com outra chave, ou com chave armazenada inválida, é rejeitado")
    void registrationRejectedOtherKey() throws Exception {
        when(deviceRepository.findByHardwareId(HW)).thenReturn(Optional.of(device));
        String other = pem(generate("secp256r1"));

        DeviceRegistrationResult different = registration().register(payload(HW, other, null));
        assertEquals(Status.REJECTED, different.status());
        assertEquals("Dispositivo já cadastrado com outra chave pública", different.message());
        assertEquals(null, different.deviceId());

        device.setPublicKey("corrompida");
        assertEquals(Status.REJECTED, registration().register(payload(HW, pem(keyPair), null)).status());
    }

    @Test
    @DisplayName("Corrida no cadastro: relê o dispositivo e decide pelo estado final")
    void registrationRace() {
        when(deviceRepository.findByHardwareId(HW)).thenReturn(Optional.empty(), Optional.of(device));
        when(deviceRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        DeviceRegistrationResult result = registration().register(payload(HW, pem(keyPair), null));

        assertEquals(Status.ALREADY_REGISTERED, result.status());
    }

    @Test
    @DisplayName("Falha de integridade sem dispositivo concorrente devolve REJECTED")
    void registrationRaceWithoutDevice() {
        when(deviceRepository.findByHardwareId(HW)).thenReturn(Optional.empty(), Optional.empty());
        when(deviceRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        DeviceRegistrationResult result = registration().register(payload(HW, pem(keyPair), null));

        assertEquals(Status.REJECTED, result.status());
        assertEquals("Não foi possível registrar o dispositivo", result.message());
    }

    @Test
    @DisplayName("Assinatura inválida interrompe o registro antes de consultar o banco")
    void registrationSignatureFailure() {
        org.mockito.Mockito.doThrow(new InvalidDeviceSignatureException())
                .when(proofVerifier).verifyRegistrationSignature(any(), any(), any(), any());

        assertThrows(InvalidDeviceSignatureException.class,
                () -> registration().register(payload(HW, pem(keyPair), null)));
        verify(deviceRepository, never()).findByHardwareId(any());
    }
}
