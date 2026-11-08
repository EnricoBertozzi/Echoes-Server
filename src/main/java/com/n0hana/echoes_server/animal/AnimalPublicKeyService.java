package com.n0hana.echoes_server.animal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.n0hana.echoes_server.animal.exception.InvalidPublicKeyException;

/**
 * Responsável por ler e interpretar a chave pública EC enviada no cadastro do
 * animal.
 *
 * <p>
 * O arquivo <b>não é armazenado</b>: apenas o seu conteúdo textual (PEM) é
 * extraído e devolvido para ser persistido no campo {@code publicKey} do
 * animal.
 * </p>
 */
@Service
public class AnimalPublicKeyService {

    private static final int ES256_FIELD_SIZE = 256;

    /**
     * Lê o conteúdo do arquivo de chave pública e valida se ele contém uma chave
     * EC P-256 válida.
     *
     * @param file arquivo multipart enviado na requisição
     * @return conteúdo PEM da chave pública
     */
    public String readFrom(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new InvalidPublicKeyException();

        try {
            String pem = new String(file.getBytes(), StandardCharsets.UTF_8).trim();
            parse(pem);
            return pem;
        } catch (IOException exception) {
            throw new InvalidPublicKeyException();
        }
    }

    /**
     * Converte o texto PEM em uma chave pública EC.
     *
     * @param pem conteúdo PEM da chave pública
     * @return {@link ECPublicKey} pronta para validar assinaturas ES256
     */
    public ECPublicKey parse(String pem) {
        if (pem == null || pem.isBlank())
            throw new InvalidPublicKeyException();

        String publicKeyContent = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        try {
            byte[] decoded = Base64.getDecoder().decode(publicKeyContent);
            PublicKey publicKey = KeyFactory.getInstance("EC")
                    .generatePublic(new X509EncodedKeySpec(decoded));

            if (!(publicKey instanceof ECPublicKey ecPublicKey))
                throw new InvalidPublicKeyException();

            int fieldSize = ecPublicKey.getParams().getCurve().getField().getFieldSize();
            if (fieldSize != ES256_FIELD_SIZE)
                throw new InvalidPublicKeyException();

            return ecPublicKey;
        } catch (InvalidPublicKeyException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidPublicKeyException();
        }
    }
}
