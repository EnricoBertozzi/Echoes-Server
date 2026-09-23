package com.n0hana.echoes_server.cnpj.client;

import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.n0hana.echoes_server.cnpj.CnpjDTO;
import com.n0hana.echoes_server.cnpj.config.CnpjProperties;
import com.n0hana.echoes_server.cnpj.exception.CnpjInvalidoException;
import com.n0hana.echoes_server.cnpj.exception.CnpjNaoEncontradoException;
import com.n0hana.echoes_server.cnpj.exception.CnpjProviderIndisponivelException;

/**
 * Cliente HTTP para a Brasil API.
 *
 * <p>
 * O {@link RestClient.Builder} é injetado (não construído internamente) para
 * permitir {@code MockRestServiceServer} em testes.
 * </p>
 */
@Component
public class BrasilApiClient {

    private final RestClient restClient;

    public BrasilApiClient(RestClient.Builder builder, CnpjProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(properties.getTimeoutSeconds()).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(properties.getTimeoutSeconds()).toMillis());

        this.restClient = builder
            .clone()
            .baseUrl(properties.getBrasilApi().getUrl())
            .requestFactory(factory)
            .defaultHeader("User-Agent", "Echoes-Server/1.0")
            .build();
    }

    /**
     * Consulta um CNPJ na Brasil API.
     *
     * @throws CnpjNaoEncontradoException        404 — CNPJ não existe na Receita
     * @throws CnpjInvalidoException             400 — formato rejeitado pela API
     * @throws CnpjProviderIndisponivelException 5xx, timeout, connection refused
     */
    public CnpjDTO consultar(String cnpj) {
        try {
            return restClient.get()
                .uri("/api/cnpj/v1/{cnpj}", cnpj)
                .exchange((request, response) -> {
                    HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());

                    if (status == HttpStatus.OK) {
                        CnpjDTO body = response.bodyTo(CnpjDTO.class);
                        if (body == null) {
                            throw new CnpjProviderIndisponivelException("BrasilAPI retornou corpo vazio");
                        }
                        return body;
                    }
                    if (status == HttpStatus.NOT_FOUND) {
                        throw new CnpjNaoEncontradoException("CNPJ não encontrado na Receita Federal");
                    }
                    if (status == HttpStatus.BAD_REQUEST) {
                        throw new CnpjInvalidoException("CNPJ rejeitado pela BrasilAPI");
                    }
                    throw new CnpjProviderIndisponivelException("BrasilAPI indisponível: " + status);
                });
        } catch (RestClientException e) {
            // Timeout, DNS, connection refused, resposta malformada — tudo vira 503
            throw new CnpjProviderIndisponivelException("BrasilAPI inacessível: " + e.getMessage());
        }
    }
}
