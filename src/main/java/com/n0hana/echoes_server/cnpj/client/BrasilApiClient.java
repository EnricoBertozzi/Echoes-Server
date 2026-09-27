package com.n0hana.echoes_server.cnpj.client;

import java.net.SocketTimeoutException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.n0hana.echoes_server.cnpj.CnpjDTO;
import com.n0hana.echoes_server.cnpj.config.CnpjProperties;
import com.n0hana.echoes_server.cnpj.exception.CnpjInvalidoException;
import com.n0hana.echoes_server.cnpj.exception.CnpjNaoEncontradoException;
import com.n0hana.echoes_server.cnpj.exception.CnpjProviderIndisponivelException;

import lombok.extern.slf4j.Slf4j;

/**
 * Cliente HTTP para a Brasil API.
 *
 * <p>
 * O {@link RestClient.Builder} é injetado (não construído internamente) para
 * permitir {@code MockRestServiceServer} em testes.
 * </p>
 *
 * <p>
 * Diagnóstico de indisponibilidade: 429 (rate limit) e timeouts de socket são
 * logados individualmente para que uma indisponibilidade em massa (IP
 * bloqueado, quota estourada, rede lenta) seja distinguível de um 5xx isolado.
 * </p>
 */
@Slf4j
@Component
public class BrasilApiClient {

    private final RestClient restClient;

    public BrasilApiClient(RestClient.Builder builder, CnpjProperties properties) {
        this.restClient = builder
            .clone()
            .baseUrl(properties.getBrasilApi().getUrl())
            .defaultHeader("User-Agent", "Echoes-Server/1.0")
            .build();
    }

    /**
     * Consulta um CNPJ na Brasil API.
     *
     * @throws CnpjNaoEncontradoException        404 — CNPJ não existe na Receita
     * @throws CnpjInvalidoException             400 — formato rejeitado pela API
     * @throws CnpjProviderIndisponivelException 429, 5xx, timeout, connection refused
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
                            log.warn("BrasilAPI retornou corpo vazio para o CNPJ {}", cnpj);
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
                    if (status == HttpStatus.TOO_MANY_REQUESTS) {
                        log.warn("BrasilAPI rate limit (HTTP 429) ao consultar CNPJ {}. "
                                + "Verifique a configuração do cache Caffeine ou reduza a frequência.",
                                cnpj);
                        throw new CnpjProviderIndisponivelException("BrasilAPI rate limit excedido (429)");
                    }
                    log.error("BrasilAPI retornou status inesperado {} ao consultar CNPJ {}", status, cnpj);
                    throw new CnpjProviderIndisponivelException("BrasilAPI indisponível: " + status);
                });
        } catch (ResourceAccessException e) {
            // ResourceAccessException é subclasse de RestClientException e envolve
            // falhas de I/O: timeout de socket, connection refused, DNS, etc.
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException) {
                log.warn("Timeout ao consultar BrasilAPI (CNPJ {}): {}", cnpj, e.getMessage());
            } else {
                log.warn("Falha de conexão com BrasilAPI (CNPJ {}): {}", cnpj, e.getMessage());
            }
            throw new CnpjProviderIndisponivelException("BrasilAPI inacessível: " + e.getMessage());
        } catch (RestClientException e) {
            // Demais erros do RestClient (status inesperado, desserialização, etc.).
            // As exceções customizadas lançadas dentro do lambda NÃO caem aqui —
            // elas propagam intactas porque não herdam de RestClientException.
            log.warn("Erro de comunicação com BrasilAPI (CNPJ {}): {}", cnpj, e.getMessage());
            throw new CnpjProviderIndisponivelException("BrasilAPI inacessível: " + e.getMessage());
        }
    }
}
