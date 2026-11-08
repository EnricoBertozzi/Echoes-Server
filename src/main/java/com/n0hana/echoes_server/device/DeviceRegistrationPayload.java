package com.n0hana.echoes_server.device;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Payload publicado pelo dispositivo no tópico MQTT de autocadastro.
 *
 * <p>
 * O campo {@code signature} contém a assinatura ECDSA P-256/SHA-256 sobre a
 * mensagem canônica definida em {@link DeviceSignatureService}. A chave pública
 * é aceita tanto como {@code publicKey} quanto como {@code public_key}.
 * </p>
 *
 * @param mac       MAC do dispositivo
 * @param publicKey chave pública EC P-256 em PEM (SPKI)
 * @param signature assinatura da mensagem canônica
 * @param name      nome opcional de exibição; quando ausente, usa-se o MAC
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeviceRegistrationPayload(
    String mac,
    @JsonAlias("public_key") String publicKey,
    @JsonAlias("sig") String signature,
    String name) {
}
