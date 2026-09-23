package com.n0hana.echoes_server.cnpj;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.n0hana.echoes_server.cnpj.client.BrasilApiClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orquestrador de consulta de CNPJ.
 *
 * <p>
 * Valida o formato localmente, consulta o cache Caffeine (TTL 24h) e, em
 * caso de miss, delega ao {@link BrasilApiClient}. Sucesso é cacheado;
 * falhas {@code 4xx} e {@code 5xx} propagam como exceções tipadas.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CnpjService {

    private final BrasilApiClient brasilApiClient;
    private final Cache<String, CnpjDTO> cache;

    public CnpjDTO consultar(String rawCnpj) {
        String cnpj = CnpjValidator.normalizarEValidar(rawCnpj);

        CnpjDTO cached = cache.getIfPresent(cnpj);
        if (cached != null) {
            log.info("Consulta CNPJ {} via cache", cnpj);
            return cached;
        }

        CnpjDTO dados = brasilApiClient.consultar(cnpj);
        cache.put(cnpj, dados);
        log.info("Consulta CNPJ {} via BrasilAPI", cnpj);
        return dados;
    }
}
