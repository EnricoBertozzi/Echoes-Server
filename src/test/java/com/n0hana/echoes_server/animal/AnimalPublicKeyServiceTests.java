package com.n0hana.echoes_server.animal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.n0hana.echoes_server.animal.exception.InvalidPublicKeyException;

public class AnimalPublicKeyServiceTests {

    private final AnimalPublicKeyService publicKeyService = new AnimalPublicKeyService();

    @Test
    @DisplayName("Lê e converte uma chave pública EC P-256 em PEM")
    void shouldParseEcPublicKey() throws Exception {
        String pem = generatePem();

        ECPublicKey publicKey = publicKeyService.parse(pem);

        assertEquals(256, publicKey.getParams().getCurve().getField().getFieldSize());
    }

    @Test
    @DisplayName("Lê o conteúdo do arquivo sem armazená-lo")
    void shouldReadPublicKeyFile() throws Exception {
        String pem = generatePem();
        MockMultipartFile file = new MockMultipartFile(
                "publicKey",
                "public-key.pem",
                "text/plain",
                pem.getBytes());

        assertEquals(pem, publicKeyService.readFrom(file));
    }

    @Test
    @DisplayName("Rejeita arquivo vazio")
    void shouldRejectEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "publicKey",
                "public-key.pem",
                "text/plain",
                new byte[0]);

        assertThrows(InvalidPublicKeyException.class, () -> publicKeyService.readFrom(file));
    }

    @Test
    @DisplayName("Rejeita conteúdo que não é uma chave pública EC")
    void shouldRejectInvalidContent() {
        assertThrows(InvalidPublicKeyException.class, () -> publicKeyService.parse("conteudo invalido"));
    }

    private String generatePem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();

        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }
}
