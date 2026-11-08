package com.n0hana.echoes_server.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.n0hana.echoes_server.device.exception.InvalidDevicePublicKeyException;

public class DevicePublicKeyServiceTests {

    private final DevicePublicKeyService publicKeyService = new DevicePublicKeyService();

    @Test
    @DisplayName("Lê e converte uma chave pública EC P-256 em PEM")
    void shouldParseEcPublicKey() throws Exception {
        String pem = generatePem("secp256r1");

        ECPublicKey publicKey = publicKeyService.parse(pem);

        assertEquals(256, publicKey.getParams().getCurve().getField().getFieldSize());
    }

    @Test
    @DisplayName("Rejeita conteúdo que não é uma chave pública EC")
    void shouldRejectInvalidContent() {
        assertThrows(InvalidDevicePublicKeyException.class, () -> publicKeyService.parse("conteudo invalido"));
    }

    @Test
    @DisplayName("Rejeita chave pública nula ou vazia")
    void shouldRejectEmptyKey() {
        assertThrows(InvalidDevicePublicKeyException.class, () -> publicKeyService.parse(null));
        assertThrows(InvalidDevicePublicKeyException.class, () -> publicKeyService.parse("   "));
    }

    @Test
    @DisplayName("Rejeita chave pública que não seja P-256")
    void shouldRejectNonP256Key() throws Exception {
        String pem = generatePem("secp384r1");

        assertThrows(InvalidDevicePublicKeyException.class, () -> publicKeyService.parse(pem));
    }

    private String generatePem(String curve) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec(curve));
        KeyPair keyPair = generator.generateKeyPair();

        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }
}
