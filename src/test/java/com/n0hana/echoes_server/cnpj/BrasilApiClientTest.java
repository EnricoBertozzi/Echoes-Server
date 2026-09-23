package com.n0hana.echoes_server.cnpj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

import com.n0hana.echoes_server.cnpj.client.BrasilApiClient;
import com.n0hana.echoes_server.cnpj.config.CnpjProperties;
import com.n0hana.echoes_server.cnpj.exception.CnpjInvalidoException;
import com.n0hana.echoes_server.cnpj.exception.CnpjNaoEncontradoException;
import com.n0hana.echoes_server.cnpj.exception.CnpjProviderIndisponivelException;

@RestClientTest(BrasilApiClient.class)
@EnableConfigurationProperties(CnpjProperties.class)
@TestPropertySource(properties = {
    "cnpj.brasil-api.url=https://brasilapi.com.br",
    "cnpj.timeout-seconds=2"
})
class BrasilApiClientTest {

    private static final String CNPJ = "19131243000197";
    private static final String URL = "https://brasilapi.com.br/api/cnpj/v1/" + CNPJ;

    @Autowired private BrasilApiClient client;
    @Autowired private MockRestServiceServer server;

    @Test
    @DisplayName("200 mapeia snake_case da API para camelCase do DTO")
    void retorna200ComDados() {
        server.expect(requestTo(URL))
            .andRespond(withSuccess(
                "{\"cnpj\":\"" + CNPJ + "\",\"razao_social\":\"EMPRESA TESTE\",\"cep\":\"01001000\"}",
                MediaType.APPLICATION_JSON));

        CnpjDTO result = client.consultar(CNPJ);

        assertEquals(CNPJ, result.cnpj());
        assertEquals("EMPRESA TESTE", result.razaoSocial());
        assertEquals("01001000", result.cep());
    }

    @Test
    @DisplayName("404 lança CnpjNaoEncontradoException")
    void lancaCnpjNaoEncontradoNo404() {
        server.expect(requestTo(URL))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(CnpjNaoEncontradoException.class, () -> client.consultar(CNPJ));
    }

    @Test
    @DisplayName("400 lança CnpjInvalidoException")
    void lancaCnpjInvalidoNo400() {
        server.expect(requestTo(URL))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThrows(CnpjInvalidoException.class, () -> client.consultar(CNPJ));
    }

    @Test
    @DisplayName("500 lança CnpjProviderIndisponivelException")
    void lancaIndisponivelNo500() {
        server.expect(requestTo(URL))
            .andRespond(withServerError());

        assertThrows(CnpjProviderIndisponivelException.class, () -> client.consultar(CNPJ));
    }

    @Test
    @DisplayName("200 com corpo vazio lança CnpjProviderIndisponivelException")
    void corpoVazioLancaIndisponivel() {
        server.expect(requestTo(URL))
            .andRespond(withStatus(HttpStatus.OK));

        assertThrows(CnpjProviderIndisponivelException.class, () -> client.consultar(CNPJ));
    }

    @Test
    @DisplayName("Resposta 200 com conteúdo é desserializada corretamente")
    void respostaComConteudoEhDesserializada() {
        server.expect(requestTo(URL))
            .andRespond(withSuccess(
                "{\"cnpj\":\"" + CNPJ + "\",\"razao_social\":\"X\",\"nome_fantasia\":\"Y\"}",
                MediaType.APPLICATION_JSON));

        CnpjDTO result = client.consultar(CNPJ);

        assertTrue(result.razaoSocial().equals("X"));
        assertTrue(result.nomeFantasia().equals("Y"));
    }
}
