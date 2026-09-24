package com.n0hana.echoes_server.cnpj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.benmanes.caffeine.cache.Cache;
import com.n0hana.echoes_server.cnpj.client.BrasilApiClient;
import com.n0hana.echoes_server.cnpj.exception.CnpjInvalidoException;
import com.n0hana.echoes_server.cnpj.exception.CnpjNaoEncontradoException;
import com.n0hana.echoes_server.cnpj.exception.CnpjProviderIndisponivelException;

@ExtendWith(MockitoExtension.class)
class CnpjServiceTest {

    private static final String CNPJ = "19131243000197";

    @Mock private BrasilApiClient brasilApiClient;
    @Mock private Cache<String, CnpjDTO> cache;

    private CnpjService service;

    @BeforeEach
    void setUp() {
        service = new CnpjService(brasilApiClient, cache);
    }

    private CnpjDTO dto() {
    return new CnpjDTO(CNPJ, "Empresa Teste", null, null, null, null, null, null, null, null, null);
    //                cnpj   razaoSocial   fantasia logradouro num comp bairro municipio uf cep telefone
    }



    @Test
    @DisplayName("Cache hit → retorna do cache sem chamar Brasil API")
    void cacheHitNaoChamaApi() {
        when(cache.getIfPresent(CNPJ)).thenReturn(dto());

        CnpjDTO result = service.consultar(CNPJ);

        assertEquals("Empresa Teste", result.razaoSocial());
        verify(brasilApiClient, never()).consultar(anyString());
    }

    @Test
    @DisplayName("Cache miss → consulta Brasil API e popula o cache")
    void cacheMissConsultaApiEPopula() {
        when(cache.getIfPresent(CNPJ)).thenReturn(null);
        when(brasilApiClient.consultar(CNPJ)).thenReturn(dto());

        CnpjDTO result = service.consultar(CNPJ);

        assertEquals("Empresa Teste", result.razaoSocial());
        verify(cache).put(CNPJ, result);
    }

    @Test
    @DisplayName("CNPJ com formato inválido → CnpjInvalidoException sem chamar API")
    void cnpjFormatoInvalidoNaoChamaApi() {
        assertThrows(CnpjInvalidoException.class, () -> service.consultar("123"));
        verify(brasilApiClient, never()).consultar(anyString());
    }

    @Test
    @DisplayName("Brasil API indisponível → propaga sem cachear")
    void apiIndisponivelPropagaSemCachear() {
        when(cache.getIfPresent(CNPJ)).thenReturn(null);
        when(brasilApiClient.consultar(CNPJ))
            .thenThrow(new CnpjProviderIndisponivelException("503"));

        assertThrows(CnpjProviderIndisponivelException.class, () -> service.consultar(CNPJ));
        verify(cache, never()).put(anyString(), any());
    }

    @Test
    @DisplayName("CNPJ não encontrado → propaga sem cachear")
    void cnpjNaoEncontradoPropagaSemCachear() {
        when(cache.getIfPresent(CNPJ)).thenReturn(null);
        when(brasilApiClient.consultar(CNPJ))
            .thenThrow(new CnpjNaoEncontradoException("não encontrado"));

        assertThrows(CnpjNaoEncontradoException.class, () -> service.consultar(CNPJ));
        verify(cache, never()).put(anyString(), any());
    }
}
